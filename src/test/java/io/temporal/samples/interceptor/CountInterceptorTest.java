/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.samples.interceptor;

import static org.junit.Assert.*;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.samples.interceptor.activities.MyActivitiesImpl;
import io.temporal.samples.interceptor.workflow.MyChildWorkflowImpl;
import io.temporal.samples.interceptor.workflow.MyWorkflow;
import io.temporal.samples.interceptor.workflow.MyWorkflowImpl;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactoryOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CountInterceptorTest {

  private static final String TASK_QUEUE = "test-queue";

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient client;

  @Before
  public void setUp() {

    TestEnvironmentOptions testEnvironmentOptions =
        TestEnvironmentOptions.newBuilder()
            .setWorkerFactoryOptions(
                WorkerFactoryOptions.newBuilder()
                    .setWorkerInterceptors(InterceptorStarter.interceptor)
                    .build())
            .build();

    testEnv = TestWorkflowEnvironment.newInstance(testEnvironmentOptions);
    worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(MyWorkflowImpl.class, MyChildWorkflowImpl.class);
    worker.registerActivitiesImplementations(new MyActivitiesImpl());

    client = testEnv.getWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testInterceptor() {
    MyWorkflow workflow =
        client.newWorkflowStub(
            MyWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

    testEnv.start();

    WorkflowClient.start(workflow::exec);

    workflow.signalNameAndTitle("John", "Customer");

    String name = workflow.queryName();
    String title = workflow.queryTitle();

    assertNotNull(name);
    assertEquals("John", name);
    assertNotNull(title);
    assertEquals("Customer", title);

    try {
      Thread.sleep(2000);
    } catch (Exception e) {
      fail("Thread.sleep error");
    }

    // this count includes both workflow and child workflow (thus 2)
    assertEquals(
        2, InterceptorStarter.interceptor.getCountCollector().getWorkflowInfoList().size());
    assertEquals(
        2,
        InterceptorStarter.interceptor
            .getCountCollector()
            .getActivitiesExecutionContextList()
            .size());
    assertEquals(1, InterceptorStarter.interceptor.getCountCollector().getSignalsInfoList().size());
    assertEquals(2, InterceptorStarter.interceptor.getCountCollector().getQueriesInfoList().size());
  }
}