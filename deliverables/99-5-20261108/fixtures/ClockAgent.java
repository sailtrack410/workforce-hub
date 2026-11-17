import java.io.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
public final class ClockAgent {
 public static void premain(String argument, Instrumentation instrumentation) {
  instrumentation.addTransformer(new ClassFileTransformer() {
   public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] input) {
    if (!name.equals("com/workforcehub/common/util/IdGenerator")) return null;
    try {
     var in = new DataInputStream(new ByteArrayInputStream(input));
     var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
     out.writeInt(in.readInt()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort());
     int count=in.readUnsignedShort();out.writeShort(count);
     for(int i=1;i<count;i++) {
      int tag=in.readUnsignedByte();out.writeByte(tag);
      switch(tag) {
       case 1: String value=in.readUTF();out.writeUTF(value.equals("java/lang/System")?"ClockFixture":value);break;
       case 3: case 4: out.writeInt(in.readInt());break;
       case 5: case 6: out.writeLong(in.readLong());i++;break;
       case 7: case 8: case 16: case 19: case 20: out.writeShort(in.readUnsignedShort());break;
       case 9: case 10: case 11: case 12: case 17: case 18: out.writeInt(in.readInt());break;
       case 15: out.writeByte(in.readUnsignedByte());out.writeShort(in.readUnsignedShort());break;
       default: throw new IOException("constant tag="+tag);
      }
     }
     out.write(in.readAllBytes());return bytes.toByteArray();
    } catch(Exception failure) { throw new IllegalStateException(failure); }
   }
  });
 }
}