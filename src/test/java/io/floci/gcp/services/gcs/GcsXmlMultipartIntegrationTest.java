package io.floci.gcp.services.gcs;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GcsXmlMultipartIntegrationTest {
    String bucket() {
        String name = "xml-" + UUID.randomUUID();
        given().contentType("application/json").body("{\"name\":\"" + name + "\"}").post("/storage/v1/b?project=test-project").then().statusCode(200);
        return name;
    }
    String initiate(String path) { return given().post(path + "?uploads").then().statusCode(200).extract().xmlPath().getString("InitiateMultipartUploadResult.UploadId"); }
    @Test void orderedPartsByteIntegrityAndAbort() {
        String bucket = bucket(), path = "/" + bucket + "/nested/file";
        String id = initiate(path);
        byte[] first = new byte[5 * 1024 * 1024]; java.util.Arrays.fill(first, (byte) 123);
        String etag = given().body(first).put(path + "?uploadId=" + id + "&partNumber=1").then().statusCode(200).extract().header("ETag");
        String last = given().body("tail").put(path + "?uploadId=" + id + "&partNumber=2").then().statusCode(200).extract().header("ETag");
        assertEquals(etag, given().body(first).put(path + "?uploadId=" + id + "&partNumber=1").header("ETag"));
        assertEquals("1", given().get(path + "?uploadId=" + id + "&max-parts=1").xmlPath().getString("ListPartsResult.NextPartNumberMarker"));
        given().get("/storage/v1/b/" + bucket + "/o/nested%2Ffile").then().statusCode(404);
        String body = "<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>" + etag + "</ETag></Part><Part><PartNumber>2</PartNumber><ETag>" + last + "</ETag></Part></CompleteMultipartUpload>";
        given().contentType("application/xml").body(body).post(path + "?uploadId=" + id).then().statusCode(200);
        byte[] result = given().get(path).then().statusCode(200).extract().asByteArray();
        assertEquals(first.length + 4, result.length);
        assertArrayEquals(first, java.util.Arrays.copyOf(result, first.length));
        assertEquals("tail", new String(java.util.Arrays.copyOfRange(result, first.length, result.length)));
        assertNull(given().get("/storage/v1/b/" + bucket + "/o/nested%2Ffile").jsonPath().get("md5Hash"));
        given().get(path + "?uploadId=" + id).then().statusCode(404);
        String abort = initiate(path);
        given().delete(path + "?uploadId=" + abort).then().statusCode(204);
        given().delete(path + "?uploadId=" + abort).then().statusCode(404);
        given().get(path).then().statusCode(200);
    }
    @Test void malformedXmlInvalidReceiptsAndUploadBinding() {
        String bucket = bucket(), path = "/" + bucket + "/file";
        String id = initiate(path);
        String etag = given().body("bytes").put(path + "?uploadId=" + id + "&partNumber=1").then().statusCode(200).extract().header("ETag");
        given().contentType("application/xml").body("<CompleteMultipartUpload><Part>").post(path + "?uploadId=" + id).then().statusCode(400);
        given().contentType("application/xml").body("<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>wrong</ETag></Part></CompleteMultipartUpload>").post(path + "?uploadId=" + id).then().statusCode(400);
        given().body("bad").put("/" + bucket + "/different?uploadId=" + id + "&partNumber=1").then().statusCode(404);
        given().header("Content-MD5", "not-a-checksum").body("bad").put(path + "?uploadId=" + id + "&partNumber=1").then().statusCode(400);
        assertEquals(etag, given().get(path + "?uploadId=" + id).xmlPath().getString("ListPartsResult.Part.ETag"));
        assertEquals(id, given().get("/" + bucket + "?uploads&prefix=fi").xmlPath().getString("ListMultipartUploadsResult.Upload.UploadId"));
    }

    @Test void checksumHeadersDescribePartAndCompletedObject() {
        String bucket = bucket(), path = "/" + bucket + "/checksums";
        String id = initiate(path);
        var part = given().body("123456789").put(path + "?uploadId=" + id + "&partNumber=1")
                .then().statusCode(200).extract().response();
        // Fixed vectors make this independent of the implementation's checksum helpers.
        assertEquals("crc32c=4waSgw==,md5=JfnnlDI7RTiF9RgfG2JNCw==", part.header("x-goog-hash"));
        String body = "<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>"
                + part.header("ETag") + "</ETag></Part></CompleteMultipartUpload>";
        var completed = given().contentType("application/xml").body(body).post(path + "?uploadId=" + id)
                .then().statusCode(200).extract().response();
        assertEquals("crc32c=4waSgw==", completed.header("x-goog-hash"));
        assertEquals("123456789", given().get(path).then().statusCode(200).extract().asString());
        var metadata = given().get("/storage/v1/b/" + bucket + "/o/checksums").jsonPath();
        assertEquals("4waSgw==", metadata.getString("crc32c"));
        assertNull(metadata.get("md5Hash"));
    }

    @Test void undersizedNonFinalPartReturnsInvalidArgumentAndPreservesUpload() {
        String bucket = bucket(), path = "/" + bucket + "/small";
        String id = initiate(path);
        String first = given().body("small").put(path + "?uploadId=" + id + "&partNumber=1").header("ETag");
        String last = given().body("tail").put(path + "?uploadId=" + id + "&partNumber=2").header("ETag");
        String body = "<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>" + first
                + "</ETag></Part><Part><PartNumber>2</PartNumber><ETag>" + last
                + "</ETag></Part></CompleteMultipartUpload>";
        assertEquals("InvalidArgument", given().contentType("application/xml").body(body)
                .post(path + "?uploadId=" + id).then().statusCode(400).extract().xmlPath().getString("Error.Code"));
        given().get(path).then().statusCode(404);
        assertEquals(2, given().get(path + "?uploadId=" + id).xmlPath().getList("ListPartsResult.Part").size());
        given().delete(path + "?uploadId=" + id).then().statusCode(204);
    }
}
