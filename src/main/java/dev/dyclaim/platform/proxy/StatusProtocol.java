package dev.dyclaim.platform.proxy;

import java.io.*;
import java.util.*;

/** Backend status protocol. It cannot carry commands, claims, balances or mutations. */
public final class StatusProtocol {
    public static final String CHANNEL="dyclaim:status";
    public static final int MAX_BYTES=4096;
    private static final int MAGIC=0x44594353,VERSION=1,REQUEST=1,RESPONSE=2,MAX_CAPABILITIES=32;
    public record Status(UUID requestId,String version,Set<String> capabilities){
        public Status {
            Objects.requireNonNull(requestId);Objects.requireNonNull(version);Objects.requireNonNull(capabilities);
            if(version.length()>48||!version.matches("[A-Za-z0-9]+(?:[._+\\-][A-Za-z0-9]+)*"))throw new IllegalArgumentException("Invalid version");
            if(capabilities.size()>MAX_CAPABILITIES)throw new IllegalArgumentException("Too many capabilities");
            for(String capability:capabilities)if(capability==null||!capability.matches("[a-z][a-z0-9-]{0,31}"))throw new IllegalArgumentException("Invalid capability");
            capabilities=Set.copyOf(capabilities);
        }
    }
    private StatusProtocol(){}
    public static byte[] request(UUID id){return encode(REQUEST,Objects.requireNonNull(id),null);}
    public static UUID decodeRequest(byte[] bytes)throws IOException{
        try(DataInputStream input=input(bytes,REQUEST)){UUID id=readId(input);finish(input);return id;}
    }
    public static byte[] response(Status status){Objects.requireNonNull(status);return encode(RESPONSE,status.requestId(),status);}
    public static Status decodeStatus(byte[] bytes)throws IOException{
        try(DataInputStream input=input(bytes,RESPONSE)){
            UUID id=readId(input);String version=input.readUTF();int count=input.readUnsignedByte();
            if(count>MAX_CAPABILITIES)throw new IOException("Too many capabilities");
            Set<String> capabilities=new HashSet<>();for(int i=0;i<count;i++)if(!capabilities.add(input.readUTF()))throw new IOException("Duplicate capability");
            finish(input);try{return new Status(id,version,capabilities);}catch(IllegalArgumentException e){throw new IOException("Invalid status",e);}
        }
    }
    private static byte[] encode(int kind,UUID id,Status status){
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes)){
            output.writeInt(MAGIC);output.writeByte(VERSION);output.writeByte(kind);output.writeLong(id.getMostSignificantBits());output.writeLong(id.getLeastSignificantBits());
            if(status!=null){output.writeUTF(status.version());output.writeByte(status.capabilities().size());for(String capability:new TreeSet<>(status.capabilities()))output.writeUTF(capability);}
            output.flush();byte[] payload=bytes.toByteArray();if(payload.length>MAX_BYTES)throw new IllegalArgumentException("Status payload too large");return payload;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static DataInputStream input(byte[] bytes,int expectedKind)throws IOException{
        if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("Invalid payload size");
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        if(input.readInt()!=MAGIC||input.readUnsignedByte()!=VERSION||input.readUnsignedByte()!=expectedKind)throw new IOException("Unknown protocol or message kind");return input;
    }
    private static UUID readId(DataInputStream input)throws IOException{return new UUID(input.readLong(),input.readLong());}
    private static void finish(DataInputStream input)throws IOException{if(input.read()!=-1)throw new IOException("Unexpected trailing data");}
}
