/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.luke.app.desktop.util;

import org.apache.lucene.luke.app.desktop.MessageBroker;
import org.apache.lucene.luke.models.LukeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

  public static void handle(Throwable t, MessageBroker messageBroker) {
    if (t instanceof LukeException) {
      Throwable cause = t.getCause();
      String message = (cause == null) ? t.getMessage() : t.getMessage() + " " + cause.getMessage();
      logger.warn(t.getMessage(), t);
      messageBroker.showStatusMessage(message);
    } else {
      logger.error(t.getMessage(), t);
      messageBroker.showUnknownErrorMessage();
    }
  }

}
