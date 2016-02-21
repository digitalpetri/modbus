/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.examples;

import java.util.concurrent.ExecutionException;

import com.digitalpetri.modbus.examples.master.MasterExample;
import com.digitalpetri.modbus.examples.slave.SlaveExample;

public class MasterSlaveThroughput {

    public static final int N_MASTERS = 100;
    public static final int N_REQUESTS = 100;

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        new SlaveExample().start();
        new MasterExample(N_MASTERS, N_REQUESTS).start();
    }

}
