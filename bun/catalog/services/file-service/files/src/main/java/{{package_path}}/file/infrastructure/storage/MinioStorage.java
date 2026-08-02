package {{package}}.file.infrastructure.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
public class MinioStorage implements MediaStorage {
    private final MinioClient client;
    private final String bucket;
    public MinioStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }
    @Override
    public CompletableFuture<Void> putAsync(String key, InputStream content, long contentLength, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(content, contentLength, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new MediaStorageException("MinIO put failed for " + key, e));
            return failed;
        }
        return CompletableFuture.completedFuture(null);
    }
    @Override
    public MediaObject get(String key) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(key).build());
            InputStream stream = client.getObject(io.minio.GetObjectArgs.builder()
                    .bucket(bucket).object(key).build());
            return new MediaObject(stream,
                    stat.contentType() != null ? stat.contentType() : "application/octet-stream",
                    stat.size(), stat.etag());
        } catch (ErrorResponseException e) {
            throw new MediaNotFoundException(key);
        } catch (Exception e) {
            throw new MediaStorageException("MinIO get failed for " + key, e);
        }
    }
    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new MediaStorageException("MinIO delete failed for " + key, e);
        }
    }
    @Override
    public URI presignedPutUrl(String key, Duration ttl, String contentType) {
        try {
            return URI.create(client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket).object(key)
                    .expiry((int) ttl.toSeconds())
                    .build()));
        } catch (Exception e) {
            throw new MediaStorageException("MinIO presign PUT failed for " + key, e);
        }
    }
    @Override
    public URI presignedGetUrl(String key, Duration ttl) {
        try {
            return URI.create(client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket).object(key)
                    .expiry((int) ttl.toSeconds())
                    .build()));
        } catch (Exception e) {
            throw new MediaStorageException("MinIO presign GET failed for " + key, e);
        }
    }
}
