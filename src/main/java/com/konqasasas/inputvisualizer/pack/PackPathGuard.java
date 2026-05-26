package com.konqasasas.inputvisualizer.pack;
public final class PackPathGuard { private PackPathGuard(){} public static boolean isSafeTexturePath(String p){ if(p==null) return false; String n=p.replace('\\','/'); return n.startsWith("textures/") && !n.contains("..") && !n.startsWith("/") && !n.contains(":") && n.toLowerCase().endsWith(".png"); } }
