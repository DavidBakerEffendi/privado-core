package ai.privado.entrypoint

import ai.privado.exporter.JSONExporter
import ai.privado.model.{NodeType, RuleInfo, Rules}
import ai.privado.semantic.Language._
import better.files.File
import io.circe.yaml.parser
import io.joern.javasrc2cpg.{Config, JavaSrc2Cpg}
import io.joern.joerncli.DefaultOverlays
import io.joern.x2cpg.X2Cpg.applyDefaultOverlays
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.sys.exit
import scala.util.{Failure, Success}

object ScanProcessor extends CommandProcessor {
  val logger = LoggerFactory.getLogger(this.getClass)

  def parseRules(rulesPath: String): Rules = {
    val ir: File =
      try File(rulesPath)
      catch {
        case ex: Throwable =>
          logger.debug("File error: ", ex)
          logger.error(f"Rules path '${rulesPath}' is not accessible")
          exit(1)
      }
    val parsedRules =
      try
        ir.listRecursively
          .filter(f => f.extension == Some(".yaml") || f.extension == Some(".YAML"))
          .map(file => {
            val filePath            = file.pathAsString
            val fileName            = file.nameWithoutExtension
            val immediateParentName = file.parent.nameWithoutExtension
            parser.parse(file.contentAsString) match {
              case Right(json) =>
                import ai.privado.model.CirceEnDe._
                json.as[Rules] match {
                  case Right(rules) =>
                    rules.copy(
                      sources = rules.sources.map(x =>
                        x.copy(
                          filePath = filePath,
                          fileName = fileName,
                          parentName = immediateParentName,
                          nodeType = NodeType.SOURCE
                        )
                      ),
                      sinks = rules.sinks.map(x =>
                        x.copy(
                          filePath = filePath,
                          fileName = fileName,
                          parentName = immediateParentName,
                          nodeType = NodeType.withNameWithDefault(immediateParentName)
                        )
                      )
                    )
                  case _ =>
                    Rules(List[RuleInfo](), List[RuleInfo]())
                }
              case _ =>
                Rules(List[RuleInfo](), List[RuleInfo]())
            }
          })
          .reduce((a, b) => a.copy(sources = a.sources ++ b.sources, sinks = a.sinks ++ b.sinks))
      catch {
        case ex: Throwable =>
          logger.debug("File error: ", ex)
          logger.error(f"Rules path '${rulesPath}' is not accessible")
          exit(1)
      }
    parsedRules
  }

  def processRules(): Rules = {
    var internalRules = Rules(List[RuleInfo](), List[RuleInfo]())
    if (!config.ignoreInternalRules) {
      internalRules = parseRules(config.internalRulesPath.head)
    }
    var externalRules = Rules(List[RuleInfo](), List[RuleInfo]())
    if (!config.externalRulePath.isEmpty) {
      externalRules = parseRules(config.externalRulePath.head)
    }
    /*
     * NOTE: We want to override the external rules over internal in case of duplicates by id.
     * While concatenating two lists (internal and external) and get the distinct list of elements.
     * Elements from the first collection will be kept and elements from second collection will be discarded.
     *
     * e.g
     * val sources     = externalRules.sources ++ internalRules.sources
     * sources.distinctBy(_.id) - this will return unique list of elements duplicated by id.
     * In case of duplicates it will keep the elements from "externalRules.sources".
     * We don't know the internal logic. We came to this conclusion based on testing few samples.
     */
    val sources     = externalRules.sources ++ internalRules.sources
    val sinks       = externalRules.sinks ++ internalRules.sinks
    val mergedRules = Rules(sources.distinctBy(_.id), sinks.distinctBy(_.id))
    logger.info(mergedRules.toString())
    mergedRules
  }
  override def process(): Unit = {
    println("Hello Joern")
    println("Creating CPG... ")
    processCPG(processRules())
  }

  def processCPG(processedRules: Rules): Unit = {
    val sourceRepoLocation = config.sourceLocation.head
    import io.joern.console.cpgcreation.guessLanguage
    val xtocpg = guessLanguage(sourceRepoLocation) match {
      case Some(Languages.JAVASRC) =>
        val cpgconfig =
          Config(inputPaths = Set(sourceRepoLocation), skipDependencyDownload = config.skipDownladDependencies)
        JavaSrc2Cpg().createCpg(cpgconfig)

      case _ =>
        Failure(new RuntimeException("Language Not Detected"))
    }
    xtocpg match {
      case Success(cpgWithoutDataflow) =>
        println("[DONE]")
        println("Applying default overlays")
        cpgWithoutDataflow.close()
        val cpg = DefaultOverlays.create("cpg.bin")
        println("Printing all methods:")
        println("=====================")

        val rules: List[RuleInfo] = processedRules.sources ++ processedRules.sinks
        println("Rules discovered")

        // Run tagger
        cpg.runTagger(rules)
        val dataflows = cpg.dataflow.l

        // Attach each dataflow with a unique id
        val dataflowMap = dataflows.map(dataflow => (UUID.randomUUID().toString, dataflow)).toMap

        // Exporting
        val outputFileName = "privado"
        JSONExporter.fileExport(cpg, outputFileName, sourceRepoLocation, dataflowMap)

        // Utility to debug
        for (tagName <- cpg.tag.name.dedup.l) {
          val tags = cpg.tag(tagName).l
          println(s"tag Name : ${tagName}, size : ${tags.size}")
          println("Values : ")
          for (tag <- tags) {
            print(s"${tag.value}, ")
          }
          println("\n----------------------------------------")
        }
      case Failure(exception) =>
        println("[FAILED]")
        println(exception)
    }
  }

  override var config: PrivadoInput = _
}