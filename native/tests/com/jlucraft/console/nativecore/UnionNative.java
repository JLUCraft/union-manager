package com.jlucraft.console.nativecore;
public class UnionNative {
  static {System.loadLibrary("union_manager");}
  public static native byte[] generateSecret();
  public static native String peerId(byte[] secret);
  public static native String execute(byte[] secret,String input);
  public static void main(String[] args) {
    byte[] secret=generateSecret();
    String first=peerId(secret);
    if(!first.equals(peerId(secret.clone())))throw new AssertionError("identity changed");
    reject(()->peerId(new byte[0]));
    reject(()->peerId(new byte[4097]));
    reject(()->peerId(new byte[]{1,2,3}));
    reject(()->execute(secret,"{invalid"));
    reject(()->execute(secret,"x".repeat(65537)));
    java.util.Arrays.fill(secret,(byte)0);
    reject(()->peerId(secret));
    System.out.println("JNI transient identity and invalid input rejection passed");
  }
  private static void reject(Runnable action) {
    try {action.run();}catch(IllegalStateException error){return;}
    throw new AssertionError("invalid request accepted");
  }
}
