package io.floci.gcp.test;

import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.ServiceOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GcsServiceAccountAuthenticationTest {

    @Test
    void serviceAccountCredentialsAuthenticateStorageRequests() throws Exception {
        var credentials = TestFixtures.serviceAccountCredentials();
        List<String> authorizationHeaders = new ArrayList<>();
        HttpTransportOptions transport = new HttpTransportOptions(HttpTransportOptions.newBuilder()) {
            @Override
            public HttpRequestInitializer getHttpRequestInitializer(ServiceOptions<?, ?> options) {
                HttpRequestInitializer initializer = super.getHttpRequestInitializer(options);
                return request -> {
                    initializer.initialize(request);
                    HttpExecuteInterceptor interceptor = request.getInterceptor();
                    request.setInterceptor(outgoing -> {
                        if (interceptor != null) {
                            interceptor.intercept(outgoing);
                        }
                        authorizationHeaders.add(outgoing.getHeaders().getAuthorization());
                    });
                };
            }
        };

        String bucketName = TestFixtures.uniqueName("service-account-auth");
        try (Storage storage = StorageOptions.newBuilder()
                .setHost(TestFixtures.endpoint())
                .setProjectId(TestFixtures.projectId())
                .setCredentials(credentials)
                .setTransportOptions(transport)
                .build()
                .getService()) {
            storage.create(BucketInfo.of(bucketName));

            try {
                assertThat(storage.get(bucketName)).isNotNull();
                // Scoped service-account credentials exchange their assertion at /token.
                // Check the emitted token on the wire, allowing SDK credential copies and retries.
                assertThat(authorizationHeaders).hasSizeGreaterThanOrEqualTo(2).allSatisfy(header ->
                        assertThat(header).startsWith("Bearer floci-gcp-"));
            } finally {
                assertThat(storage.delete(bucketName)).isTrue();
            }
        }
    }
}
