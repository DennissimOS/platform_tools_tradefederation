/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.command.remote;

import com.android.tradefed.command.remote.CommandResult.Status;
import com.android.tradefed.device.FreeDeviceState;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Remote operation for getting the result of the last command executed
 */
class GetLastCommandResultOp extends RemoteOperation<CommandResult> {

    // input json keys
    private static final String SERIAL = "serial";
    private static final String FREE_DEVICE_STATE = "free_device_state";
    private static final String STATUS = "status";
    private static final String INVOCATION_ERROR = "invocation_error";

    private final String mSerial;

    GetLastCommandResultOp(String serial) {
        mSerial = serial;
    }

    /**
     * Factory method for creating a {@link GetLastCommandResultOp} from JSON data.
     *
     * @param json the data as a {@link JSONObject}
     * @return a {@link GetLastCommandResultOp}
     * @throws JSONException if failed to extract out data
     */
    static GetLastCommandResultOp createFromJson(JSONObject json) throws JSONException {
        return new GetLastCommandResultOp(json.getString(SERIAL));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected OperationType getType() {
        return OperationType.GET_LAST_COMMAND_RESULT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void packIntoJson(JSONObject json) throws JSONException {
        json.put(SERIAL, mSerial);
    }

    /**
     * Unpacks the response from remote TF manager into this object.
     */
    @Override
    protected CommandResult unpackResponseFromJson(JSONObject json) throws JSONException {
        Status status = Status.NOT_ALLOCATED;
        FreeDeviceState state = FreeDeviceState.AVAILABLE;
        String statusString = json.getString(STATUS);
        try {
            status = CommandResult.Status.valueOf(statusString);
        } catch (IllegalArgumentException e) {
            throw new JSONException(String.format("unrecognized status '%s'", statusString));
        }

        String errorDetails = json.optString(INVOCATION_ERROR, null);
        String freeDeviceString = json.optString(FREE_DEVICE_STATE, null);
        if (freeDeviceString != null) {
            try {
                state = FreeDeviceState.valueOf(freeDeviceString);
            } catch (IllegalArgumentException e) {
                throw new JSONException(String.format("unrecognized state '%s'", freeDeviceString));
            }
        }
        return new CommandResult(status, errorDetails, state);
    }

    /**
     * Packs the result into the json response to send to remote client.
     */
    protected void packResponseIntoJson(CommandResult commandResult, JSONObject json)
            throws JSONException {
        json.put(STATUS, commandResult.getStatus().name());
        if (commandResult.getInvocationErrorDetails() != null) {
            json.put(INVOCATION_ERROR, commandResult.getInvocationErrorDetails());
        }
        if (commandResult.getFreeDeviceState() != null) {
            json.put(FREE_DEVICE_STATE, commandResult.getFreeDeviceState().name());
        }
    }

    public String getDeviceSerial() {
        return mSerial;
    }
}
