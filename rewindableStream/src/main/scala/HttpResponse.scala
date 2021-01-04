import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.client.methods.CloseableHttpResponse

import java.io._
import scala.util.Using

class HttpResponse(val response: CloseableHttpResponse, stream: Option[InputStream] = None)
  extends AutoCloseable {

  protected lazy val entity: Option[HttpEntity] = Option(response.getEntity)
  protected lazy val isRepeatable: Option[Boolean] = entity.map(_.isRepeatable)

  /**
   * Generates a new re-readable/rewindable HttpResponse if stream does not support reading twice.
   * If it does, sets the response stream position to the beginning.
   *
   * @return a new RewindableHttpResponse contains a stream that can be consumed multiple times.
   */
  def getRewindableHttpResponse: RewindableHttpResponse =
    stream.map(stream => new RewindableHttpResponse(response, Some(RewindableStream(stream)))) match {
      case Some(rewindableHttpResponse) => rewindableHttpResponse
      case None => getContent.map(stream => {
        new RewindableHttpResponse(response, Some(RewindableStream(stream)))
      }).getOrElse(new RewindableHttpResponse(response, None))
    }

  /** Gets the content of the entity. If the entity supports re-reading then each call returns
   * a readable stream.
   *
   * @return
   */
  protected def getContent: Option[InputStream] = entity.flatMap(e => Option(e.getContent))

  override def close(): Unit = {
    getContent.foreach(_.close())
    response.close()
  }
}

class RewindableHttpResponse(inner: CloseableHttpResponse, rewindableStream: Option[InputStream])
  extends HttpResponse(inner, rewindableStream) {

  def getStream: Option[InputStream] =
    isRepeatable.flatMap(repeatable => {
      if (repeatable)
        getContent
      else rewindableStream.map(RewindableStream(_))
    })

  def getString: Option[String] = getStream.map(s => new String(s.readAllBytes()))
}

/**
 * Creates a multiple times readable stream either by copying it to a new byte array stream
 * or resetting the existing stream to the initial position.
 * Be aware of memory consumption if the resource stream is not supporting the reset operation.
 */
object RewindableStream {
  def apply(stream: InputStream): InputStream =
    if (stream.markSupported()) {
      stream.reset()
      stream
    } else {
      Using.resource(stream)(resource => {
        Using.resource(new ByteArrayInputOutputStream())(provider => {
          IOUtils.copy(resource, provider)
          provider.getInputStream
        })
      })
    }
}

class ByteArrayInputOutputStream extends ByteArrayOutputStream {
  def getInputStream: InputStream = new ByteArrayInputStream(buf, 0, count)
}
