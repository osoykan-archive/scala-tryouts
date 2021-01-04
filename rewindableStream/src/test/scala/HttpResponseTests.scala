import org.apache.http.HttpEntity
import org.apache.http.client.methods.{CloseableHttpResponse, RequestBuilder}
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, never, times, verify, when}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.{ByteArrayInputStream, InputStream}
import scala.util.Using

class HttpResponseTests extends AnyFunSuite {

  test("When the Response has empty Entity and Content") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    when(responseMock.getEntity).thenReturn(null)

    // Act
    val response = new HttpResponse(responseMock)
    val rewindableHttpResponse = response.getRewindableHttpResponse

    // Assert
    rewindableHttpResponse.getStream shouldBe None
    rewindableHttpResponse.getString shouldBe None
  }

  test("When the Stream is repeatable then it should close 1 time the stream") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val entityMock = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(inputStream.markSupported()).thenReturn(true)
    when(inputStream.read(any[Array[Byte]])).thenReturn(-1)
    when(entityMock.getContent).thenReturn(inputStream)
    when(entityMock.isRepeatable).thenReturn(true)
    when(responseMock.getEntity).thenReturn(entityMock)

    // Act
    val response = new HttpResponse(responseMock)
    response.getRewindableHttpResponse
    response.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream).close()
  }

  test("When the Stream is repeatable, then closing the rewindableHttpResponse should also close the main httpResponse") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val entityMock = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(inputStream.markSupported()).thenReturn(true)
    when(inputStream.read(any[Array[Byte]])).thenReturn(-1)
    when(entityMock.getContent).thenReturn(inputStream)
    when(entityMock.isRepeatable).thenReturn(true)
    when(responseMock.getEntity).thenReturn(entityMock)

    // Act
    val response = new HttpResponse(responseMock)
    val rewindableHttpResponse = response.getRewindableHttpResponse
    rewindableHttpResponse.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream).close()
  }

  test("When the Stream is non-repeatable, then closing the main response should close the stream twice") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val entityMock = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(inputStream.markSupported()).thenReturn(false)
    when(inputStream.read(any[Array[Byte]])).thenReturn(-1)
    when(entityMock.getContent).thenReturn(inputStream)
    when(entityMock.isRepeatable).thenReturn(false)
    when(responseMock.getEntity).thenReturn(entityMock)

    // Act
    val response = new HttpResponse(responseMock)
    response.getRewindableHttpResponse // Because, the stream is buffer-copied a rewindable stream, and it closes the main response stream after copying it.
    response.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream, times(2)).close()
  }

  test("When the Response is Closed then close the response stream as well") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val httpEntity = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(httpEntity.getContent).thenReturn(inputStream)
    when(responseMock.getEntity).thenReturn(httpEntity)

    // Act
    val response = new HttpResponse(responseMock)
    response.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream).close()
  }

  test("When the Re-Readable/Repeatable response is closed, then close the main response as well") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val httpEntity = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(inputStream.markSupported()).thenReturn(true)
    when(httpEntity.getContent).thenReturn(inputStream)
    when(responseMock.getEntity).thenReturn(httpEntity)

    // Act
    val response = new HttpResponse(responseMock)
    val rewindableHttpResponse = response.getRewindableHttpResponse
    rewindableHttpResponse.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream).close()
  }

  test("When the main Response(super) is closed, then close Rewindable response as well") {

    // Arrange
    val responseMock = mock[CloseableHttpResponse]
    val httpEntity = mock[HttpEntity]
    val inputStream = mock[InputStream]
    when(inputStream.markSupported()).thenReturn(true)
    when(httpEntity.getContent).thenReturn(inputStream)
    when(responseMock.getEntity).thenReturn(httpEntity)

    // Act
    val response = new HttpResponse(responseMock)
    val rewindable = response.getRewindableHttpResponse
    response.close()

    // Assert
    verify(responseMock).close()
    verify(inputStream).close()
    verify(rewindable.getStream.get).close()
  }

  test("When source stream is re-readable, then it uses getEntity to retrieve stream from source") {

    // Arrange
    val streamMock = mock[InputStream]
    when(streamMock.markSupported()).thenReturn(true)

    val httpEntity = mock[HttpEntity]
    when(httpEntity.getContent).thenReturn(streamMock)
    when(httpEntity.isRepeatable).thenReturn(true)

    val responseMock = mock[CloseableHttpResponse]
    when(responseMock.getEntity).thenReturn(httpEntity)

    val response = new HttpResponse(responseMock)

    // Act
    val rewindableHttpResponse = response.getRewindableHttpResponse // 1. Reset
    val rewindableStream = rewindableHttpResponse.getStream // 2. Reset
    val rewindableStream2 = rewindableHttpResponse.getStream //3. Reset

    // Assert
    verify(streamMock).reset() // So, we don't reset, because entity.getContent returns the Stream again.
    verify(streamMock, never).read(any[Array[Byte]]) //Because we never read or touch the stream
    rewindableStream shouldBe rewindableStream2
    rewindableStream.get.available() shouldBe rewindableStream2.get.available()
  }

  test("When consume getStream multiple times from Rewindable") {

    // Arrange
    val string = "content"
    val byteArrayEntity = new ByteArrayEntity(string.getBytes) // re-readable from #getContent

    val responseMock = mock[CloseableHttpResponse]
    when(responseMock.getEntity).thenReturn(byteArrayEntity)

    val response = new HttpResponse(responseMock)

    // Act
    val rewindableHttpResponse = response.getRewindableHttpResponse
    val attempt1 = rewindableHttpResponse.getString
    val attempt2 = rewindableHttpResponse.getString

    // Assert
    attempt1.get shouldBe attempt2.get
    attempt1.get shouldBe string
    attempt2.get shouldBe string
  }
}

class HttpResponseIntegrationTests extends AnyFunSuite {

  test("When source stream is NOT re-readable, then it converts source stream into byte array stream") {

    val httpClient = HttpClientBuilder.create().build()
    val url = "https://jsonplaceholder.typicode.com/todos/1"
    val httpResponse = httpClient.execute(RequestBuilder.get(url).build())
    val response = new HttpResponse(httpResponse)

    val rewindableHttpResponse = response.getRewindableHttpResponse

    val stream1 = rewindableHttpResponse.getStream
    Using.resource(stream1.get) { resource =>
      println(new String(resource.readAllBytes()))
    }

    val stream2 = rewindableHttpResponse.getStream
    Using.resource(stream2.get) { resource =>
      println(new String(resource.readAllBytes()))
    }

    val string = rewindableHttpResponse.getString
    string.foreach(println(_))

    httpClient.close()
    response.close()

    rewindableHttpResponse.getStream.get.available() should be > 0
  }

  test("large file") {

    val httpClient = HttpClientBuilder.create().build()
    val url = "http://212.183.159.230/20MB.zip"
    val httpResponse = httpClient.execute(RequestBuilder.get(url).build())
    val response = new HttpResponse(httpResponse)

    val rewindableHttpResponse = response.getRewindableHttpResponse

    val stream1 = rewindableHttpResponse.getStream
    Using.resource(stream1.get) { resource =>
      println(resource.readAllBytes().length)
    }

    val stream2 = rewindableHttpResponse.getStream
    Using.resource(stream2.get) { resource =>
      println(resource.readAllBytes().length)
    }

    httpClient.close()
    response.close()

    stream1.get.available() should be <= 0
    stream2.get.available() should be <= 0
  }
}
