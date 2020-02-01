/*
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

package com.uber.cadence.samples.hello;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import io.temporal.activity.ActivityMethod;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.workflow.Async;
import io.temporal.workflow.Functions.Func;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowMethod;

/**
 * Demonstrates asynchronous activity invocation. Requires a local instance of Cadence server to be
 * running.
 */
public class HelloAsync {

  static final String TASK_LIST = "HelloAsync";

  public interface GreetingWorkflow {
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 15, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    String composeGreeting(String greeting, String name);
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting using {@link
   * Async#function(Func)}.
   */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      // Async.invoke takes method reference and activity parameters and returns Promise.
      Promise<String> hello = Async.function(activities::composeGreeting, "Hello", name);
      Promise<String> bye = Async.function(activities::composeGreeting, "Bye", name);

      // Promise is similar to the Java Future. Promise#get blocks until result is ready.
      return hello.get() + "\n" + bye.get();
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
