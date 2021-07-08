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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.testing.TestWorkflowExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Unit test for {@link HelloActivityExclusiveChoice}. Doesn't use an external Temporal service. */
public class HelloActivityExclusiveChoiceJUnit5Test {

  @RegisterExtension
  public static final TestWorkflowExtension testWorkflowExtension =
      TestWorkflowExtension.newBuilder()
          .setWorkflowTypes(HelloActivityExclusiveChoice.PurchaseFruitsWorkflowImpl.class)
          .setActivityImplementations(new HelloActivityExclusiveChoice.OrderFruitsActivitiesImpl())
          .build();

  @Test
  public void testWorkflow(HelloActivityExclusiveChoice.PurchaseFruitsWorkflow workflow) {
    // Execute a workflow waiting for it to complete.
    HelloActivityExclusiveChoice.ShoppingList shoppingList =
        new HelloActivityExclusiveChoice.ShoppingList();
    shoppingList.addFruitOrder(HelloActivityExclusiveChoice.Fruits.APPLE, 10);
    StringBuilder orderResults = workflow.orderFruit(shoppingList);
    assertEquals("Ordered 10 Apples...", orderResults.toString());
  }
}
