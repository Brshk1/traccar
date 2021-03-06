/*
 * Copyright 2013 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar.protocol;

import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.Log;
import org.traccar.model.Event;
import org.traccar.model.Position;

public class GalileoProtocolDecoder extends BaseProtocolDecoder {

    public GalileoProtocolDecoder(GalileoProtocol protocol) {
        super(protocol);
    }

    private static final int TAG_IMEI = 0x03;
    private static final int TAG_DATE = 0x20;
    private static final int TAG_COORDINATES = 0x30;
    private static final int TAG_SPEED_COURSE = 0x33;
    private static final int TAG_ALTITUDE = 0x34;
    private static final int TAG_STATUS = 0x40;
    private static final int TAG_POWER = 0x41;
    private static final int TAG_BATTERY = 0x42;
    private static final int TAG_ODOMETER = 0xd4;
    private static final int TAG_REFRIGERATOR = 0x5b;
    private static final int TAG_PRESSURE = 0x5c;
    
    private static final Map<Integer, Integer> tagLengthMap = new HashMap<>();
    
    static {
        int[] l1 = {0x01,0x02,0x35,0x43,0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xcb,0xcc,0xcd,0xce,0xcf,0xd0,0xd1,0xd2,0xd5,0x88,0x8a,0x8b,0x8c,0xa0,0xaf,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xa9,0xaa,0xab,0xac,0xad,0xae};
        int[] l2 = {0x04,0x10,0x34,0x40,0x41,0x42,0x45,0x46,0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x60,0x61,0x62,0x70,0x71,0x72,0x73,0x74,0x75,0x76,0x77,0xb0,0xb1,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xd6,0xd7,0xd8,0xd9,0xda};
        int[] l3 = {0x63,0x64,0x6f,0x5d,0x65,0x66,0x67,0x68,0x69,0x6a,0x6b,0x6c,0x6d,0x6e};
        int[] l4 = {0x20,0x33,0x44,0x90,0xc0,0xc1,0xc2,0xc3,0xd3,0xd4,0xdb,0xdc,0xdd,0xde,0xdf,0xf0,0xf9,0x5a,0x47,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8};
        for (int i : l1) { tagLengthMap.put(i, 1); }
        for (int i : l2) { tagLengthMap.put(i, 2); }
        for (int i : l3) { tagLengthMap.put(i, 3); }
        for (int i : l4) { tagLengthMap.put(i, 4); }
        tagLengthMap.put(TAG_COORDINATES, 9);
        tagLengthMap.put(TAG_IMEI, 15);
        tagLengthMap.put(TAG_REFRIGERATOR, 7); // variable length
        tagLengthMap.put(TAG_PRESSURE, 68);
    }

    private static int getTagLength(int tag) {
        return tagLengthMap.get(tag);
    }

    private void sendReply(Channel channel, int checksum) {
        ChannelBuffer reply = ChannelBuffers.directBuffer(ByteOrder.LITTLE_ENDIAN, 3);
        reply.writeByte(0x02);
        reply.writeShort((short) checksum);
        if (channel != null) {
            channel.write(reply);
        }
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;
        
        buf.readUnsignedByte(); // header
        int length = (buf.readUnsignedShort() & 0x7fff) + 3;
        
        List<Position> positions = new LinkedList<>();
        Set<Integer> tags = new HashSet<>();
        boolean hasLocation = false;
        Position position = new Position();
        position.setProtocol(getProtocolName());
        
        while (buf.readerIndex() < length) {

            // Check if new message started
            int tag = buf.readUnsignedByte();
            if (tags.contains(tag)) {
                if (hasLocation && position.getFixTime() != null) {
                    positions.add(position);
                }
                tags.clear();
                hasLocation = false;
                position = new Position();
            }
            tags.add(tag);
            
            switch (tag) {

                case TAG_IMEI:
                    String imei = buf.toString(buf.readerIndex(), 15, Charset.defaultCharset());
                    buf.skipBytes(imei.length());
                    identify(imei, channel);
                    break;

                case TAG_DATE:
                    position.setTime(new Date(buf.readUnsignedInt() * 1000));
                    break;
                    
                case TAG_COORDINATES:
                    hasLocation = true;
                    position.setValid((buf.readUnsignedByte() & 0xf0) == 0x00);
                    position.setLatitude(buf.readInt() / 1000000.0);
                    position.setLongitude(buf.readInt() / 1000000.0);
                    break;
                    
                case TAG_SPEED_COURSE:
                    position.setSpeed(buf.readUnsignedShort() * 0.0539957);
                    position.setCourse(buf.readUnsignedShort() * 0.1);
                    break;
                    
                case TAG_ALTITUDE:
                    position.setAltitude(buf.readShort());
                    break;
                    
                case TAG_STATUS:
                    position.set(Event.KEY_STATUS, buf.readUnsignedShort());
                    break;
                    
                case TAG_POWER:
                    position.set(Event.KEY_POWER, buf.readUnsignedShort());
                    break;
                    
                case TAG_BATTERY:
                    position.set(Event.KEY_BATTERY, buf.readUnsignedShort());
                    break;
                    
                case TAG_ODOMETER:
                    position.set(Event.KEY_ODOMETER, buf.readUnsignedInt());
                    break;
                    
                default:
                    buf.skipBytes(getTagLength(tag));
                    break;
                    
            }
        }
        if (hasLocation && position.getFixTime() != null) {
            positions.add(position);
        }
        
        if (!hasDeviceId()) {
            Log.warning("Unknown device");
            return null;
        }

        sendReply(channel, buf.readUnsignedShort());

        for (Position p : positions) {
            p.setDeviceId(getDeviceId());
        }

        if (positions.isEmpty()) {
            return null;
        }
        return positions;
    }

}
