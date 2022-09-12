package ai.privado.exporter

import ai.privado.model.Constants
import ai.privado.model.exporter.{CollectionModel, DataFlowSubCategoryModel, SourceModel, SourceProcessingModel}

import java.net.URL
import scala.collection.mutable
import Console.{BLUE, BOLD, CYAN, GREEN, MAGENTA, RED, RESET, WHITE, YELLOW}

object ConsoleExporter {

  private def getDomainFromString(urlString: String): String = {
    val url = new URL("https://" + urlString.replaceAll("https://", "").trim)
    url.getHost.replaceAll("www.", "").replaceAll("\"", "")
  }

  def exportConsoleSummary(
    dataflowsOutput: mutable.LinkedHashMap[String, List[DataFlowSubCategoryModel]],
    sources: List[SourceModel],
    processing: List[SourceProcessingModel],
    collections: List[CollectionModel],
    violationSize: Int
  ): Unit = {
    // SourceId - Name Map
    val sourceNameIdMap = sources.map((source) => (source.id, source.name)).toMap

    // Leakage Number - SourceId Map
    val leakageSourceMap = dataflowsOutput(Constants.leakages).map(
      (leakage) => (leakage.sourceId, leakage.sinks.size)
    ).toMap

    // Processing Number - SourceId Map
    val processSourceMap = processing.map(
      process => (process.sourceId -> process.occurrences.size)
    ).toMap

    // Collections Names - SourceId Map
    val collectionsSourceMap = mutable.HashMap[String, mutable.Set[String]]()
    collections.foreach(collection => {
      collection.collections.foreach(collect => {
        collect.occurrences.foreach(occur => {
          if (collectionsSourceMap.contains(collect.sourceId)) {
            collectionsSourceMap(collect.sourceId).addOne(occur.endPoint)
          } else {
            collectionsSourceMap.addOne(collect.sourceId -> mutable.Set[String](occur.endPoint))
          }
        })
      })
    })

    // Storages - SourceId Map
    val storageSourceMap = dataflowsOutput(Constants.storages).map(
      storage => (storage.sourceId, storage.sinks.map(sink => sink.name).toSet)
    ).toMap
    val uniqueStorages = storageSourceMap.values.flatten.toSet

    // Third Parties - SourceId Map
    val thirdPartySourceMap = dataflowsOutput(Constants.third_parties).map(thirdParty => {
      val thirdParties = mutable.Set[String]()
      thirdParty.sinks.foreach(sink => {
        if (sink.apiUrl.size > 0) {
          sink.apiUrl.foreach(urlString => {
            thirdParties.addOne(getDomainFromString(urlString))
          })
        } else {
          thirdParties.addOne(sink.name)
        }
      })
      (thirdParty.sourceId, thirdParties)
    }).toMap
    val uniqueThirdParties = thirdPartySourceMap.values.flatten.toSet

    // Internal APIs - SourceId Map
    val internalAPIsSourceMap = dataflowsOutput(Constants.internal_apis).map(internalAPI => {
      val internalAPIs = mutable.Set[String]()
      internalAPI.sinks.foreach(sink => {
        if (sink.apiUrl.size > 0) {
          sink.apiUrl.foreach(urlString => {
            internalAPIs.addOne(getDomainFromString(urlString))
          })
        }
      })
      (internalAPI.sourceId, internalAPIs)
    }).toMap

    println("\n-----------------------------------------------------------")
    println("SUMMARY")
    println("-----------------------------------------------------------")
    println("\nPrivado discovers data elements that are being collected, processed, or shared in the code.\n")
    println(s"DATA ELEMENTS  |  ${sourceNameIdMap.size} |")
    println(s"THIRD PARTY    |  ${uniqueThirdParties.size} |")
    println(s"STORAGES       |  ${uniqueStorages.size} |")
    println(s"ISSUES         |  ${violationSize} |")
    println("\n---------------------------------------------------------")
    if (sourceNameIdMap.size > 0) {
      println(s"${sourceNameIdMap.size} DATA ELEMENTS")
      println("Here is a list of data elements discovered in the code along with details on data flows to third parties, databases and leakages to logs.")
    }

    var count = 0;
    sourceNameIdMap.foreachEntry((sourceId, sourceName) => {
      count = count + 1

      Console.println(s"\n${RESET}${WHITE}${BOLD}${count}. ${sourceName.toUpperCase()}${RESET}")

      if (thirdPartySourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${YELLOW}Sharing${RESET}         ->  ${thirdPartySourceMap(sourceId).toList.mkString(", ")}")
      }
      if (storageSourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${BLUE}Storage${RESET}         ->  ${storageSourceMap(sourceId).toList.mkString(", ")}")
      }
      if (internalAPIsSourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${CYAN}Internal API${RESET}    ->  ${internalAPIsSourceMap(sourceId).toList.mkString(", ")}")
      }
      if (leakageSourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${RED}Leakage${RESET}         ->  ${leakageSourceMap(sourceId)}")
      }
      if (collectionsSourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${GREEN}Collections${RESET}     ->  ${collectionsSourceMap(sourceId).toList.mkString(", ")}")
      }
      if (processSourceMap.contains(sourceId)) {
        Console.println(s"\t${RESET}${MAGENTA}Processing${RESET}      ->  ${processSourceMap(sourceId)}")
      }
    })

    println("")

  }

}