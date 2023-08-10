package ai.privado.languageEngine.ruby.passes

import ai.privado.languageEngine.ruby.passes.PrivadoAstCreationPass.producerQueueCapacity
import io.joern.rubysrc2cpg.Config
import io.joern.rubysrc2cpg.astcreation.{AstCreator, ResourceManagedParser}
import io.joern.rubysrc2cpg.passes.AstCreationPass
import io.joern.rubysrc2cpg.utils.{PackageContext, PackageTable}
import io.joern.x2cpg.SourceFiles
import io.joern.x2cpg.datastructures.Global
import io.shiftleft.SerializedCpg
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.passes.ConcurrentWriterCpgPass
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.utils.ExecutionContextProvider
import org.slf4j.{LoggerFactory, MDC}
import overflowdb.BatchedUpdate
import concurrent.duration.DurationInt

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.concurrent.LinkedBlockingQueue
object PrivadoAstCreationPass {
  private val writerQueueCapacity   = 4
  private val producerQueueCapacity = 2 + 4 * Runtime.getRuntime().availableProcessors()
}
class PrivadoAstCreationPass(
  cpg: Cpg,
  global: Global,
  parser: ResourceManagedParser,
  packageTable: PackageTable,
  config: Config
) extends AstCreationPass(cpg, global, parser, packageTable, config) {
  private val logger = LoggerFactory.getLogger(this.getClass)
  override def createApplySerializeAndStore(
    serializedCpg: SerializedCpg,
    inverse: Boolean = false,
    prefix: String = ""
  ): Unit = {
    import ConcurrentWriterCpgPass.producerQueueCapacity
    baseLogger.info(s"Start of enhancement: $name")
    val nanosStart = System.nanoTime()
    var nParts     = 0
    var nDiff      = 0
    nDiffT = -1
    init()
    val parts = generateParts()
    nParts = parts.size
    val partIter        = parts.iterator
    val completionQueue = mutable.ArrayDeque[Future[overflowdb.BatchedUpdate.DiffGraph]]()
    val writer          = new Writer(MDC.getCopyOfContextMap())
    val writerThread    = new Thread(writer)
    writerThread.setName("Writer")
    writerThread.start()
    implicit val ec: ExecutionContext = ExecutionContextProvider.getExecutionContext
    try {
      try {
        // The idea is that we have a ringbuffer completionQueue that contains the workunits that are currently in-flight.
        // We add futures to the end of the ringbuffer, and take futures from the front.
        // then we await the future from the front, and add it to the writer-queue.
        // the end result is that we get deterministic output (esp. deterministic order of changes), while having up to one
        // writer-thread and up to producerQueueCapacity many threads in-flight.
        // as opposed to ParallelCpgPass, there is no race between diffgraph-generators to enqueue into the writer -- everything
        // is nice and ordered. Downside is that a very slow part may gum up the works (i.e. the completionQueue fills up and threads go idle)
        var done = false
        while (!done && writer.raisedException == null) {
          if (writer.raisedException != null)
            throw writer.raisedException // will be wrapped with good stacktrace in the finally block

          if (completionQueue.size < producerQueueCapacity && partIter.hasNext) {
            val next = partIter.next()
            // todo: Verify that we get FIFO scheduling; otherwise, do something about it.
            // if this e.g. used LIFO with 4 cores and 18 size of ringbuffer, then 3 cores may idle while we block on the front item.
            completionQueue.append(Future.apply {
              val builder = new DiffGraphBuilder
              runOnPart(builder, next)
              builder.build()
            })
          } else if (completionQueue.nonEmpty) {
            val future = completionQueue.removeHead()
            try {
              val res = Await.result(future, 60.seconds)
              nDiff += res.size
              writer.queue.put(Some(res))
            } catch {
              case ex: Exception =>
                logger.error(s"Timeout error while parsing file", ex)
            }
          } else {
            done = true
          }
        }
      } finally {
        try {
          // if the writer died on us, then the queue might be full and we could deadlock
          if (writer.raisedException == null) writer.queue.put(None)
          writerThread.join()
          // we need to reraise exceptions
          if (writer.raisedException != null)
            throw new RuntimeException("Failure in diffgraph application", writer.raisedException)

        } finally {
          finish()
        }
      }
    } finally {
      // the nested finally is somewhat ugly -- but we promised to clean up with finish(), we want to include finish()
      // in the reported timings, and we must have our final log message if finish() throws

      val nanosStop = System.nanoTime()

      baseLogger.info(
        f"Enhancement $name completed in ${(nanosStop - nanosStart) * 1e-6}%.0f ms. ${nDiff}%d  + ${nDiffT - nDiff}%d changes committed from ${nParts}%d parts."
      )
    }
  }

  private class Writer(mdc: java.util.Map[String, String]) extends Runnable {

    val queue =
      new LinkedBlockingQueue[Option[overflowdb.BatchedUpdate.DiffGraph]](PrivadoAstCreationPass.writerQueueCapacity)

    @volatile var raisedException: Exception = null

    override def run(): Unit = {
      try {
        nDiffT = 0
        // logback chokes on null context maps
        if (mdc != null) MDC.setContextMap(mdc)
        var terminate  = false
        var index: Int = 0
        while (!terminate) {
          queue.take() match {
            case None =>
              baseLogger.debug("Shutting down WriterThread")
              terminate = true
            case Some(diffGraph) =>
              nDiffT += overflowdb.BatchedUpdate
                .applyDiff(cpg.graph, diffGraph, null, null)
                .transitiveModifications()
              index += 1
          }
        }
      } catch {
        case exception: InterruptedException => baseLogger.warn("Interrupted WriterThread", exception)
        case exc: Exception =>
          raisedException = exc
          queue.clear()
          throw exc
      }
    }
  }
}
