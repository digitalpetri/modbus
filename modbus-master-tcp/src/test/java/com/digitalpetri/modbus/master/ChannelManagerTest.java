package com.digitalpetri.modbus.master;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

public class ChannelManagerTest {

    @Test
    public void testDisconnectWhenIdle() throws Exception {
        ChannelManager channelManager = new ChannelManager(null);

        channelManager.disconnect().get(1, TimeUnit.SECONDS);
    }

}