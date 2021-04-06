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

package io.temporal.samples.hello;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Sample Temporal workflow that demonstrates the workflow compensation capability.
 *
 * <p>Compensation deals with undoing or reversing work which has already successfully completed.
 * (also called SAGA). Temporal includes very powerful support for compensation which is showedcased
 * in this example.
 *
 * @see io.temporal.samples.bookingsaga.TripBookingSaga for another SAGA example.
 *     <p>To execute this example a locally running Temporal service instance is required. You can
 *     follow instructions on how to set up your Temporal service here:
 *     https://github.com/temporalio/temporal/blob/master/README.md#download-and-start-temporal-server-locally
 */
public class HelloSaga {

  // Define the task queue name
  static final String TASK_QUEUE = "HelloSagaTaskQueue";

  // Define our workflow unique id
  static final String WORKFLOW_ID = "HelloSagaTaskWorkflow";

  /**
   * Define the child workflow interface. It must contain at least one method annotated
   * with @WorkflowMethod
   *
   * @see io.temporal.workflow.WorkflowInterface
   * @see io.temporal.workflow.WorkflowMethod
   */
  @WorkflowInterface
  public interface ChildWorkflowOperation {

    /**
     * Define the child workflow method. This method is executed when the child workflow is started.
     * The child workflow completes when the workflow method finishes execution.
     */
    @WorkflowMethod
    void execute(int amount);
  }

  // Define the child workflow implementation. It implements our execute workflow method
  public static class ChildWorkflowOperationImpl implements ChildWorkflowOperation {

    /**
     * Define the ActivityOperation stub. Activity stubs implements activity interfaces and proxy
     * calls to it to Temporal activity invocations. Since Temporal activities are reentrant, a
     * single activity stub can be used for multiple activity invocations.
     *
     * <p>Let's take a look at each {@link ActivityOptions} defined: The "setScheduleToCloseTimeout"
     * option sets the overall timeout that our workflow is willing to wait for activity to
     * complete. For this example it is set to 10 seconds.
     */
    ActivityOperation activity =
        Workflow.newActivityStub(
            ActivityOperation.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(10)).build());

    @Override
    public void execute(int amount) {
      activity.execute(amount);
    }
  }

  /**
   * Define the child workflow compensation interface. It must contain at least one method annotated
   * with @WorkflowMethod
   *
   * @see io.temporal.workflow.WorkflowInterface
   * @see io.temporal.workflow.WorkflowMethod
   */
  @WorkflowInterface
  public interface ChildWorkflowCompensation {

    /**
     * Define the child workflow compensation method. This method is executed when the child
     * workflow is started. The child workflow completes when the workflow method finishes
     * execution.
     */
    @WorkflowMethod
    void compensate(int amount);
  }

  // Define the child workflow compensation implementation. It implements our compensate child
  // workflow method
  public static class ChildWorkflowCompensationImpl implements ChildWorkflowCompensation {

    /**
     * Define the ActivityOperation stub. Activity stubs implements activity interfaces and proxy
     * calls to it to Temporal activity invocations. Since Temporal activities are reentrant, a
     * single activity stub can be used for multiple activity invocations.
     *
     * <p>Let's take a look at each {@link ActivityOptions} defined: The "setScheduleToCloseTimeout"
     * option sets the overall timeout that our workflow is willing to wait for activity to
     * complete. For this example it is set to 10 seconds.
     */
    ActivityOperation activity =
        Workflow.newActivityStub(
            ActivityOperation.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(10)).build());

    @Override
    public void compensate(int amount) {
      activity.compensate(amount);
    }
  }

  /**
   * Define the Activity Interface. Workflow methods can call activities during execution.
   * Annotating activity methods with @ActivityMethod is optional
   *
   * @see io.temporal.activity.ActivityInterface
   * @see io.temporal.activity.ActivityMethod
   */
  @ActivityInterface
  public interface ActivityOperation {
    @ActivityMethod
    void execute(int amount);

    @ActivityMethod
    void compensate(int amount);
  }

  /**
   * Implementation of our workflow activity interface. It overwrites our defined execute and
   * compensate activity methods.
   */
  public static class ActivityOperationImpl implements ActivityOperation {

    @Override
    public void execute(int amount) {
      System.out.println("ActivityOperationImpl.execute() is called with amount " + amount);
    }

    @Override
    public void compensate(int amount) {
      System.out.println("ActivityCompensationImpl.compensate() is called with amount " + amount);
    }
  }

  /**
   * Define the main workflow interface. It must contain at least one method annotated
   * with @WorkflowMethod
   *
   * @see io.temporal.workflow.WorkflowInterface
   * @see io.temporal.workflow.WorkflowMethod
   */
  @WorkflowInterface
  public interface SagaWorkflow {

    /**
     * Define the workflow method. This method is executed when the workflow is started. The
     * workflow completes when the workflow method finishes execution.
     */
    @WorkflowMethod
    void execute();
  }

  // Define the main workflow implementation. It implements our execute workflow method
  public static class SagaWorkflowImpl implements SagaWorkflow {

    /**
     * Define the GreetingActivities stub. Activity stubs implements activity interfaces and proxy
     * calls to it to Temporal activity invocations. Since Temporal activities are reentrant, a
     * single activity stub can be used for multiple activity invocations.
     *
     * <p>Let's take a look at each {@link ActivityOptions} defined: The "setScheduleToCloseTimeout"
     * option sets the overall timeout that our workflow is willing to wait for activity to
     * complete. For this example it is set to 2 seconds.
     */
    ActivityOperation activity =
        Workflow.newActivityStub(
            ActivityOperation.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(2)).build());

    @Override
    public void execute() {

      // {@link io.temporal.workflow.Saga} implements the logic to perform compensation operations
      Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

      try {

        /**
         * First we show how to compensate sync child workflow invocations. We first create a child
         * workflow stub and execute its "execute" method. Then we create a stub of the child
         * compensation workflow and register it with Saga. At this point this compensation workflow
         * is not invoked. It is invoked explicitly when we actually want to invoke compensation
         * (via saga.compensate()).
         */
        ChildWorkflowOperation op1 = Workflow.newChildWorkflowStub(ChildWorkflowOperation.class);
        op1.execute(10);
        ChildWorkflowCompensation c1 =
            Workflow.newChildWorkflowStub(ChildWorkflowCompensation.class);
        saga.addCompensation(c1::compensate, -10);

        /**
         * Now we show compensation of workflow activities which are invoked asynchronously. We
         * invoke the activity "execute" method async. Then we register its "compensate" method as
         * the compensation method for it.
         *
         * <p>Again note that the compensation of this activity again is only explicitly invoked
         * (via saga.compensate()).
         */
        Promise<Void> result = Async.procedure(activity::execute, 20);
        saga.addCompensation(activity::compensate, -20);
        // get the result of the activity (blocking)
        result.get();

        /*
         * You can also supply an arbitrary lambda expression as a saga
         * compensation function.
         * Note that this compensation function is not associated with a child workflow
         * method or an activity method. It is associated with the currently executing
         * workflow method.
         *
         * Also note that here in this example we use System.out in the main workflow logic.
         * In production make sure to use Workflow.getLogger to log messages from workflow code.
         */
        saga.addCompensation(
            () -> System.out.println("Other compensation logic in main workflow."));

        /*
         * Here we throw a runtime exception on purpose to showcase
         * how to trigger compensation in case of an exception.
         * Note that compensation can be also triggered
         * without a specific exception being thrown. You can built in
         * compensation to be part of your core workflow business requirements,
         * meaning it can be triggered as part of your business logic.
         */
        throw new RuntimeException("some error");

      } catch (Exception e) {
        // we catch our exception and trigger workflow compensation
        saga.compensate();
      }
    }
  }

  /**
   * With our Workflow and Activities defined, we can now start execution. The main method is our
   * workflow starter.
   */
  public static void main(String[] args) {
    /*
     * Define the workflow service. It is a gRPC stubs wrapper which talks to the docker instance of
     * our locally running Temporal service.
     */
    WorkflowServiceStubs service = WorkflowServiceStubs.newInstance();

    /*
     * Define the workflow client. It is a Temporal service client used to start, signal, and query
     * workflows
     */
    WorkflowClient client = WorkflowClient.newInstance(service);

    /*
     * Define the workflow factory. It is used to create workflow workers for a specific task queue.
     */
    WorkerFactory factory = WorkerFactory.newInstance(client);

    /*
     * Define the workflow worker. Workflow workers listen to a defined task queue and process
     * workflows and activities.
     */
    Worker worker = factory.newWorker(TASK_QUEUE);

    /*
     * Register our workflow implementations with the worker. Since workflows are stateful in nature,
     * we need to register our workflow types.
     */
    worker.registerWorkflowImplementationTypes(
        HelloSaga.SagaWorkflowImpl.class,
        HelloSaga.ChildWorkflowOperationImpl.class,
        HelloSaga.ChildWorkflowCompensationImpl.class);

    /*
     Register our workflow activity implementation with the worker. Since workflow activities are
     stateless and thread-safe, we need to register a shared instance.
    */
    worker.registerActivitiesImplementations(new ActivityOperationImpl());

    // Start all the workers registered for a specific task queue.
    factory.start();

    // Create our workflow options
    WorkflowOptions workflowOptions =
        WorkflowOptions.newBuilder().setWorkflowId(WORKFLOW_ID).setTaskQueue(TASK_QUEUE).build();

    // Create our workflow client stub. It is used to start our workflow execution.
    HelloSaga.SagaWorkflow workflow =
        client.newWorkflowStub(HelloSaga.SagaWorkflow.class, workflowOptions);

    // Execute our workflow
    workflow.execute();
    System.exit(0);
  }
}
