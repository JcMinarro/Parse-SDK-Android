/*
 * Copyright (c) 2015-present, Parse, LLC.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.parse;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import bolts.Task;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// For org.json
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ParseFileControllerTest {

  @After
  public void tearDown() {
    // TODO(grantland): Remove once we no longer rely on retry logic.
    ParseRequest.setDefaultInitialRetryDelay(ParseRequest.DEFAULT_INITIAL_RETRY_DELAY);
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGetCacheFile() {
    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root);

    ParseFile.State state = new ParseFile.State.Builder().name("test_file").build();
    File cacheFile = controller.getCacheFile(state);
    assertEquals(new File(root, "test_file"), cacheFile);
  }

  @Test
  public void testIsDataAvailable() throws IOException {
    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root);

    temporaryFolder.newFile("test_file");

    ParseFile.State state = new ParseFile.State.Builder().name("test_file").build();
    assertTrue(controller.isDataAvailable(state));
  }

  @Test
  public void testClearCache() throws IOException {
    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root);

    File file1 = temporaryFolder.newFile("test_file_1");
    File file2 = temporaryFolder.newFile("test_file_2");
    controller.clearCache();
    assertFalse(file1.exists());
    assertFalse(file2.exists());
  }

  //region testSaveAsync

  @Test
  public void testSaveAsyncRequest() throws Exception {
    // TODO(grantland): Verify proper command is constructed
  }

  @Test
  public void testSaveAsyncNotDirty() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseFileController controller = new ParseFileController(restClient, null);

    ParseFile.State state = new ParseFile.State.Builder()
        .url("http://example.com")
        .build();
    Task<ParseFile.State> task = controller.saveAsync(state, null, null, null, null);
    task.waitForCompletion();

    verify(restClient, times(0)).execute(any(ParseHttpRequest.class));
    assertFalse(task.isFaulted());
    assertFalse(task.isCancelled());
    assertSame(state, task.getResult());
  }

  @Test
  public void testSaveAsyncAlreadyCancelled() throws Exception {
    ParseHttpClient restClient = mock(ParseHttpClient.class);
    ParseFileController controller = new ParseFileController(restClient, null);

    ParseFile.State state = new ParseFile.State.Builder().build();
    Task<Void> cancellationToken = Task.cancelled();
    Task<ParseFile.State> task = controller.saveAsync(state, null, null, null, cancellationToken);
    task.waitForCompletion();

    verify(restClient, times(0)).execute(any(ParseHttpRequest.class));
    assertTrue(task.isCancelled());
  }

  @Test
  public void testSaveAsyncSuccess() throws Exception {
    JSONObject json = new JSONObject();
    json.put("name", "new_file_name");
    json.put("url", "http://example.com");
    String content = json.toString();

    ParseHttpResponse response = mock(ParseHttpResponse.class);
    when(response.getStatusCode()).thenReturn(200);
    when(response.getContent()).thenReturn(new ByteArrayInputStream(content.getBytes()));
    when(response.getTotalSize()).thenReturn((long) content.length());

    ParseHttpClient restClient = mock(ParseHttpClient.class);
    when(restClient.execute(any(ParseHttpRequest.class))).thenReturn(response);

    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(restClient, root);

    byte[] data = "hello".getBytes();
    ParseFile.State state = new ParseFile.State.Builder()
        .name("file_name")
        .mimeType("mime_type")
        .build();
    Task<ParseFile.State> task = controller.saveAsync(state, data, null, null, null);
    ParseFile.State result = ParseTaskUtils.wait(task);

    verify(restClient, times(1)).execute(any(ParseHttpRequest.class));
    assertEquals("new_file_name", result.name());
    assertEquals("http://example.com", result.url());
    File file = new File(root, "new_file_name");
    assertTrue(file.exists());
    assertEquals("hello", ParseFileUtils.readFileToString(file, "UTF-8"));
  }

  @Test
  public void testSaveAsyncFailure() throws Exception {
    // TODO(grantland): Remove once we no longer rely on retry logic.
    ParseRequest.setDefaultInitialRetryDelay(1L);

    ParseHttpClient restClient = mock(ParseHttpClient.class);
    when(restClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(restClient, root);

    byte[] data = "hello".getBytes();
    ParseFile.State state = new ParseFile.State.Builder()
        .build();
    Task<ParseFile.State> task = controller.saveAsync(state, data, null, null, null);
    task.waitForCompletion();

    // TODO(grantland): Abstract out command runner so we don't have to account for retries.
    verify(restClient, times(5)).execute(any(ParseHttpRequest.class));
    assertTrue(task.isFaulted());
    Exception error = task.getError();
    assertThat(error, instanceOf(ParseException.class));
    assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
    assertEquals(0, root.listFiles().length);
  }

  //endregion

  //region testFetchAsync

  @Test
  public void testFetchAsyncRequest() {
    // TODO(grantland): Verify proper command is constructed
  }

  @Test
  public void testFetchAsyncAlreadyCancelled() throws Exception{
    ParseHttpClient awsClient = mock(ParseHttpClient.class);
    ParseFileController controller = new ParseFileController(null, null).awsClient(awsClient);

    ParseFile.State state = new ParseFile.State.Builder().build();
    Task<Void> cancellationToken = Task.cancelled();
    Task<File> task = controller.fetchAsync(state, null, null, cancellationToken);
    task.waitForCompletion();

    verify(awsClient, times(0)).execute(any(ParseHttpRequest.class));
    assertTrue(task.isCancelled());
  }

  @Test
  public void testFetchAsyncCached() throws Exception {
    ParseHttpClient awsClient = mock(ParseHttpClient.class);
    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root).awsClient(awsClient);

    File file = new File(root, "cached_file_name");
    ParseFileUtils.writeStringToFile(file, "hello", "UTF-8");

    ParseFile.State state = new ParseFile.State.Builder()
        .name("cached_file_name")
        .build();
    Task<File> task = controller.fetchAsync(state, null, null, null);
    File result = ParseTaskUtils.wait(task);

    verify(awsClient, times(0)).execute(any(ParseHttpRequest.class));
    assertEquals(file, result);
    assertEquals("hello", ParseFileUtils.readFileToString(result, "UTF-8"));
  }

  @Test
  public void testFetchAsyncSuccess() throws Exception {
    byte[] data = "hello".getBytes();
    ParseHttpResponse response = mock(ParseHttpResponse.class);
    when(response.getStatusCode()).thenReturn(200);
    when(response.getContent()).thenReturn(new ByteArrayInputStream(data));
    when(response.getTotalSize()).thenReturn((long) data.length);

    ParseHttpClient awsClient = mock(ParseHttpClient.class);
    when(awsClient.execute(any(ParseHttpRequest.class))).thenReturn(response);
    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root).awsClient(awsClient);

    ParseFile.State state = new ParseFile.State.Builder()
        .name("file_name")
        .build();
    Task<File> task = controller.fetchAsync(state, null, null, null);
    File result = ParseTaskUtils.wait(task);

    verify(awsClient, times(1)).execute(any(ParseHttpRequest.class));
    assertTrue(result.exists());
    assertEquals("hello", ParseFileUtils.readFileToString(result, "UTF-8"));
  }

  @Test
  public void testFetchAsyncFailure() throws Exception {
    // TODO(grantland): Remove once we no longer rely on retry logic.
    ParseRequest.setDefaultInitialRetryDelay(1L);

    ParseHttpClient awsClient = mock(ParseHttpClient.class);
    when(awsClient.execute(any(ParseHttpRequest.class))).thenThrow(new IOException());

    File root = temporaryFolder.getRoot();
    ParseFileController controller = new ParseFileController(null, root).awsClient(awsClient);

    ParseFile.State state = new ParseFile.State.Builder()
        .build();
    Task<File> task = controller.fetchAsync(state, null, null, null);
    task.waitForCompletion();

    // TODO(grantland): Abstract out command runner so we don't have to account for retries.
    verify(awsClient, times(5)).execute(any(ParseHttpRequest.class));
    assertTrue(task.isFaulted());
    Exception error = task.getError();
    assertThat(error, instanceOf(ParseException.class));
    assertEquals(ParseException.CONNECTION_FAILED, ((ParseException) error).getCode());
    assertEquals(0, root.listFiles().length);
  }

  //endregion
}
