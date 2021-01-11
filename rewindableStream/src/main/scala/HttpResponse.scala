import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.client.methods.CloseableHttpResponse

import java.io._
import scala.util.Using

class HttpResponse(val response: CloseableHttpResponse, stream: Option[InputStream] = None) extends AutoCloseable {

  protected lazy val entity: Option[HttpEntity]    = Option(response.getEntity)
  protected lazy val isRepeatable: Option[Boolean] = entity.map(_.isRepeatable)

  /**
    * Generates a new re-readable/rewindable HttpResponse, even though if the stream does not support reading twice.
    * If it does, sets the response stream position to the beginning.
    *
    * @return a new RewindableHttpResponse contains a stream that can be consumed multiple times.
    */
  def getRewindableHttpResponse: Option[RewindableHttpResponse] =
    stream.map(stream => new RewindableHttpResponse(response, Some(RewindableStream(stream)))) match {
      case rewindableHttpResponse @ Some(_) => rewindableHttpResponse
      case None =>
        getContent.map(stream => {
          new RewindableHttpResponse(response, Some(RewindableStream(stream)))
        })
    }

  def toOneTeamReadableHttpResponse: OneTimeReadableHttpResponse =
    getStream
      .map(stream => new OneTimeReadableHttpResponse(response, Some(RewindableStream(stream))))
      .getOrElse(this.asInstanceOf[OneTimeReadableHttpResponse])

  /** Gets the string while consuming the underlying entity. If you don't want to consume
    * HttpResponse, then use [[getRewindableHttpResponse]] beforehand.
    *
    * @return an Option of String which HttpResponse contains.
    */
  def getString: Option[String] = getStream.map(s => new String(s.readAllBytes()))

  /** Gets the stream by consuming the underlying entity
    * @return an Option of stream which HttpResponse contains.
    */
  def getStream: Option[InputStream] =
    stream match {
      case existing @ Some(_) => existing
      case None               => getContent
    }

  /** Gets the content of the entity. If the entity supports re-reading then each call returns
    * a readable stream.
    *
    * @return
    */
  protected def getContent: Option[InputStream] = entity.flatMap(e => Option(e.getContent))

  /**
    * Closes the response and underlying stream
    */
  override def close(): Unit = {
    getContent.foreach(_.close())
    response.close()
  }
}

class RewindableHttpResponse(inner: CloseableHttpResponse, rewindableStream: Option[InputStream])
    extends HttpResponse(inner, rewindableStream) {

  override def getStream: Option[InputStream] =
    isRepeatable.flatMap(repeatable => {
      if (repeatable) getContent
      else rewindableStream.map(RewindableStream(_))
    })

  override def getString: Option[String] = getStream.map(s => new String(s.readAllBytes()))
}

class OneTimeReadableHttpResponse(inner: CloseableHttpResponse, stream: Option[InputStream]) extends HttpResponse(inner) {
  var isRead = false

  override def getStream: Option[InputStream] =
    if (!isRead) {
      isRead = true
      stream
    } else throw new RuntimeException("stream has been read")
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
