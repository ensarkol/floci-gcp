package io.floci.gcp.test;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.HttpStorageOptions;
import com.google.cloud.storage.MultipartUploadClient;
import com.google.cloud.storage.MultipartUploadSettings;
import com.google.cloud.storage.RequestBody;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.multipartupload.model.AbortMultipartUploadRequest;
import com.google.cloud.storage.multipartupload.model.CompleteMultipartUploadRequest;
import com.google.cloud.storage.multipartupload.model.CompleteMultipartUploadResponse;
import com.google.cloud.storage.multipartupload.model.CompletedMultipartUpload;
import com.google.cloud.storage.multipartupload.model.CompletedPart;
import com.google.cloud.storage.multipartupload.model.CreateMultipartUploadRequest;
import com.google.cloud.storage.multipartupload.model.UploadPartRequest;
import com.google.cloud.storage.multipartupload.model.UploadPartResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GcsXmlMultipartChecksumTest {
    @Test
    void officialClientReadsPartAndCompletionChecksums() throws Exception {
        HttpStorageOptions options = StorageOptions.http().setHost(TestFixtures.endpoint())
                .setProjectId(TestFixtures.projectId()).setCredentials(NoCredentials.getInstance()).build();
        MultipartUploadClient client = MultipartUploadClient.create(MultipartUploadSettings.of(options));
        String bucket = TestFixtures.uniqueName("multipart-hash");
        String key = "checksum.txt";
        byte[] bytes = "123456789".getBytes(StandardCharsets.UTF_8);
        try (Storage storage = options.getService()) {
            storage.create(BucketInfo.of(bucket));
            String upload = null;
            try {
                upload = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucket).key(key).build()).uploadId();
                UploadPartRequest partRequest = UploadPartRequest.builder().bucket(bucket).key(key)
                        .uploadId(upload).partNumber(1).build();
                UploadPartResponse part = client.uploadPart(partRequest, RequestBody.of(ByteBuffer.wrap(bytes)));
                assertThat(part.crc32c()).isEqualTo("4waSgw==");
                assertThat(part.md5()).isEqualTo("JfnnlDI7RTiF9RgfG2JNCw==");
                UploadPartResponse retry = client.uploadPart(partRequest, RequestBody.of(ByteBuffer.wrap(bytes)));
                assertThat(retry.crc32c()).isEqualTo(part.crc32c());
                assertThat(retry.md5()).isEqualTo(part.md5());
                assertThat(retry.eTag()).isEqualTo(part.eTag());
                CompleteMultipartUploadResponse completed = client.completeMultipartUpload(
                        CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(upload)
                                .multipartUpload(CompletedMultipartUpload.builder().parts(List.of(
                                        CompletedPart.builder().partNumber(1).eTag(part.eTag()).build())).build()).build());
                upload = null;
                assertThat(completed.crc32c()).isEqualTo("4waSgw==");
                assertThat(storage.readAllBytes(bucket, key)).isEqualTo(bytes);
                assertThat(storage.get(bucket, key).getCrc32c()).isEqualTo(completed.crc32c());
                assertThat(storage.get(bucket, key).getMd5()).isNull();
            } finally {
                if (upload != null) {
                    client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(bucket).key(key).uploadId(upload).build());
                }
                storage.delete(bucket, key);
                storage.delete(bucket);
            }
        }
    }
}
