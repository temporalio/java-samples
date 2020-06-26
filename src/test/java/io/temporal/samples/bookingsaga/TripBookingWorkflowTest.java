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

package io.temporal.samples.bookingsaga;

import static io.temporal.samples.bookingsaga.TripBookingSaga.TASK_LIST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TripBookingWorkflowTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient client;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(TripBookingWorkflowImpl.class);

    client = testEnv.getWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  /**
   * Not very useful test that validates that the default activities cause workflow to fail. See
   * other tests on using mocked activities to test SAGA logic.
   */
  @Test
  public void testTripBookingFails() {
    worker.registerActivitiesImplementations(new TripBookingActivitiesImpl());
    testEnv.start();

    TripBookingWorkflow workflow =
        client.newWorkflowStub(
            TripBookingWorkflow.class, WorkflowOptions.newBuilder().setTaskList(TASK_LIST).build());
    try {
      workflow.bookTrip("trip1");
      fail("unreachable");
    } catch (WorkflowException e) {
      assertEquals(
          "Flight booking did not work",
          ((ApplicationFailure) e.getCause().getCause()).getOriginalMessage());
    }
  }

  /** Unit test workflow logic using mocked activities. */
  @Test
  public void testSAGA() {
    TripBookingActivities activities = mock(TripBookingActivities.class);
    when(activities.bookHotel("trip1")).thenReturn("HotelBookingID1");
    when(activities.reserveCar("trip1")).thenReturn("CarBookingID1");
    when(activities.bookFlight("trip1"))
        .thenThrow(new RuntimeException("Flight booking did not work"));
    worker.registerActivitiesImplementations(activities);

    testEnv.start();

    TripBookingWorkflow workflow =
        client.newWorkflowStub(
            TripBookingWorkflow.class, WorkflowOptions.newBuilder().setTaskList(TASK_LIST).build());
    try {
      workflow.bookTrip("trip1");
      fail("unreachable");
    } catch (WorkflowException e) {
      assertEquals(
          "Flight booking did not work",
          ((ApplicationFailure) e.getCause().getCause()).getOriginalMessage());
    }

    verify(activities).cancelHotel(eq("HotelBookingID1"), eq("trip1"));
    verify(activities).cancelCar(eq("CarBookingID1"), eq("trip1"));
  }
}
