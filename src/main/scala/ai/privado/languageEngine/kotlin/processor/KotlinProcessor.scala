package ai.privado.languageEngine.kotlin.processor

import ai.privado.audit.AuditReportEntryPoint
import ai.privado.cache.{AppCache, AuditCache, DataFlowCache, RuleCache, TaggerCache}
import ai.privado.entrypoint.ScanProcessor.config
import ai.privado.entrypoint.{ScanProcessor, TimeMetric}
import ai.privado.exporter.{ExcelExporter, JSONExporter}
import ai.privado.metric.MetricHandler
import ai.privado.model.Constants.{
  cpgOutputFileName,
  outputAuditFileName,
  outputDirectoryName,
  outputFileName,
  outputIntermediateFileName,
  outputUnresolvedFilename
}
import ai.privado.model.{CatLevelOne, Constants, Language}
import ai.privado.passes.{DBTParserPass, HTMLParserPass, SQLParser, SQLPropertyPass}
import ai.privado.semantic.Language._
import ai.privado.languageEngine.kotlin.semantic.Language._
import ai.privado.utility.{PropertyParserPass, UnresolvedReportUtility}
import ai.privado.utility.Utilities.createCpgFolder
import better.files.File
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.kotlin2cpg.Kotlin2Cpg
import io.joern.kotlin2cpg.Config
import io.joern.x2cpg.X2Cpg
import io.joern.x2cpg.passes.base.AstLinkerPass
import io.joern.x2cpg.passes.callgraph.NaiveCallLinker
import io.shiftleft.codepropertygraph
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import java.util.Calendar
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
object KotlinProcessor {
  private val logger    = LoggerFactory.getLogger(getClass)
  private var cpgconfig = Config()

  private def processCPG(
    xtocpg: Try[codepropertygraph.Cpg],
    ruleCache: RuleCache,
    sourceRepoLocation: String
  ): Either[String, Unit] = {
    xtocpg match {
      case Success(cpg) => {
        try {
          logger.info("Applying default overlays")
          logger.info("=====================")
          println(
            s"${TimeMetric.getNewTime()} - Run oss data flow is done in \t\t\t- ${TimeMetric.setNewTimeToLastAndGetTimeDiff()}"
          )

          // Apply default overlays
          X2Cpg.applyDefaultOverlays(cpg)

          // Apply OSS Dataflow overlay
          new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))

          new SQLParser(cpg, sourceRepoLocation, ruleCache).createAndApply()
          new SQLPropertyPass(cpg, sourceRepoLocation, ruleCache).createAndApply()

          // Unresolved function report
          if (config.showUnresolvedFunctionsReport) {
            val path = s"${config.sourceLocation.head}/${Constants.outputDirectoryName}"
            UnresolvedReportUtility.reportUnresolvedMethods(xtocpg, path, Language.KOTLIN)
          }

          // Run tagger
          println(s"${Calendar.getInstance().getTime} - Tagging source code with rules...")
          val taggerCache = new TaggerCache

          cpg.runTagger(ruleCache, taggerCache, privadoInputConfig = ScanProcessor.config.copy())

          println(
            s"${TimeMetric.getNewTime()} - Tagging source code is done in \t\t\t- ${TimeMetric.setNewTimeToLastAndGetTimeDiff()}"
          )

          println(s"${Calendar.getInstance().getTime} - Finding source to sink flow of data...")
          val dataflowMap = cpg.dataflow(ScanProcessor.config, ruleCache)
          println(s"\n${TimeMetric.getNewTime()} - Finding source to sink flow is done in \t\t- ${TimeMetric
              .setNewTimeToLastAndGetTimeDiff()} - Processed final flows - ${DataFlowCache.finalDataflow.size}")
          println(s"\n${TimeMetric.getNewTime()} - Code scanning is done in \t\t\t- ${TimeMetric.getTheTotalTime()}\n")
          println(s"${Calendar.getInstance().getTime} - Brewing result...")
          MetricHandler.setScanStatus(true)
          val errorMsg = new ListBuffer[String]()
          // Exporting Results
          JSONExporter.fileExport(cpg, outputFileName, sourceRepoLocation, dataflowMap, ruleCache, taggerCache) match {
            case Left(err) =>
              MetricHandler.otherErrorsOrWarnings.addOne(err)
              errorMsg += err
            case Right(_) =>
              println(s"Successfully exported output to '${AppCache.localScanPath}/$outputDirectoryName' folder")
              logger.debug(
                s"Total Sinks identified : ${cpg.tag.where(_.nameExact(Constants.catLevelOne).valueExact(CatLevelOne.SINKS.name)).call.tag.nameExact(Constants.id).value.toSet}"
              )
              Right(())
          }

          // Exporting the Audit report
          if (ScanProcessor.config.generateAuditReport) {
            ExcelExporter.auditExport(
              outputAuditFileName,
              AuditReportEntryPoint.getAuditWorkbookPy(),
              sourceRepoLocation
            ) match {
              case Left(err) =>
                MetricHandler.otherErrorsOrWarnings.addOne(err)
                errorMsg += err
              case Right(_) =>
                println(
                  s"${Calendar.getInstance().getTime} - Successfully exported Audit report to '${AppCache.localScanPath}/$outputDirectoryName' folder..."
                )
            }

            // Exporting the Unresolved report
            JSONExporter.UnresolvedFlowFileExport(
              outputUnresolvedFilename,
              sourceRepoLocation,
              DataFlowCache.getJsonFormatDataFlow(AuditCache.unfilteredFlow)
            ) match {
              case Left(err) =>
                MetricHandler.otherErrorsOrWarnings.addOne(err)
                errorMsg += err
              case Right(_) =>
                println(
                  s"${Calendar.getInstance().getTime} - Successfully exported Unresolved flow output to '${AppCache.localScanPath}/${Constants.outputDirectoryName}' folder..."
                )
            }
          }

          // Exporting the Intermediate report
          if (ScanProcessor.config.testOutput || ScanProcessor.config.generateAuditReport) {
            JSONExporter.IntermediateFileExport(
              outputIntermediateFileName,
              sourceRepoLocation,
              DataFlowCache.getJsonFormatDataFlow(DataFlowCache.getIntermediateDataFlow())
            ) match {
              case Left(err) =>
                MetricHandler.otherErrorsOrWarnings.addOne(err)
                errorMsg += err
              case Right(_) =>
                println(
                  s"${Calendar.getInstance().getTime} - Successfully exported intermediate output to '${AppCache.localScanPath}/${Constants.outputDirectoryName}' folder..."
                )
            }
          }

          // Check if any of the export failed
          if (errorMsg.toList.isEmpty)
            Right(())
          else
            Left(errorMsg.toList.mkString("\n"))
        } finally {
          cpg.close()
          import java.io.File
          val cpgFile = new File(cpgconfig.outputPath)
          println(s"\n\n\nBinary file size -- ${cpgFile.length()} in Bytes - ${cpgFile.length() * 0.000001} MB\n\n\n")
        }
      }

      case Failure(exception) =>
        logger.error("Error while parsing the source code!")
        logger.debug("Error : ", exception)
        MetricHandler.setScanStatus(false)
        Left("Error while parsing the source code: " + exception.toString)
    }
  }

  /** Create cpg using Python Language
    *
    * @param sourceRepoLocation
    * @param lang
    * @return
    */
  def createKotlinCpg(ruleCache: RuleCache, sourceRepoLocation: String, lang: String): Either[String, Unit] = {

    println(s"${Calendar.getInstance().getTime} - Processing source code using $lang engine")
    println(s"${Calendar.getInstance().getTime} - Parsing source code...")

    val cpgOutputPath = s"$sourceRepoLocation/$outputDirectoryName/$cpgOutputFileName"

    // Create the .privado folder if not present
    createCpgFolder(sourceRepoLocation);
    val cpgconfig = Config(downloadDependencies = !config.skipDownloadDependencies, includeJavaSourceFiles = true)
      .withInputPath(sourceRepoLocation)
      .withOutputPath(cpgOutputPath)
    val xtocpg = new Kotlin2Cpg().createCpg(cpgconfig).map { cpg =>
      println(
        s"${TimeMetric.getNewTime()} - Base processing done in \t\t\t\t- ${TimeMetric.setNewTimeToLastAndGetTimeDiff()}"
      )
      cpg
    }
    processCPG(xtocpg, ruleCache, sourceRepoLocation)
  }

}
