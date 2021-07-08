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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowException;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TripBookingWorkflowJUnit5Test {

  @RegisterExtension
  public static final TestWorkflowExtension testWorkflowExtension =
      TestWorkflowExtension.newBuilder()
          .setWorkflowTypes(TripBookingWorkflowImpl.class)
          .setDoNotStart(true)
          .build();

  /**
   * Not very useful test that validates that the default activities cause workflow to fail. See
   * other tests on using mocked activities to test SAGA logic.
   */
  @Test
  public void testTripBookingFails(
      TestWorkflowEnvironment testEnv, Worker worker, TripBookingWorkflow workflow) {
    worker.registerActivitiesImplementations(new TripBookingActivitiesImpl());
    testEnv.start();

    WorkflowException exception =
        assertThrows(WorkflowException.class, () -> workflow.bookTrip("trip1"));
    assertEquals(
        "Flight booking did not work",
        ((ApplicationFailure) exception.getCause().getCause()).getOriginalMessage());
  }

  /** Unit test workflow logic using mocked activities. */
  @Test
  public void testSAGA(
      TestWorkflowEnvironment testEnv, Worker worker, TripBookingWorkflow workflow) {
    TripBookingActivities activities = mock(TripBookingActivities.class);
    when(activities.bookHotel("trip1")).thenReturn("HotelBookingID1");
    when(activities.reserveCar("trip1")).thenReturn("CarBookingID1");
    when(activities.bookFlight("trip1"))
        .thenThrow(new RuntimeException("Flight booking did not work"));
    worker.registerActivitiesImplementations(activities);
    testEnv.start();

    WorkflowException exception =
        assertThrows(WorkflowException.class, () -> workflow.bookTrip("trip1"));
    assertEquals(
        "Flight booking did not work",
        ((ApplicationFailure) exception.getCause().getCause()).getOriginalMessage());

    verify(activities).cancelHotel("HotelBookingID1", "trip1");
    verify(activities).cancelCar("CarBookingID1", "trip1");
  }
}
