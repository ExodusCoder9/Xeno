package com.xeno.config;

public class XenoConfig {
	public boolean smoothLighting = true;
	public boolean ambientOcclusion = true;
	public int maxCompilesPerFrame = 8;
	public int renderDistance = 16;
	public boolean debugOverlay = false;
	public boolean asyncCulling = true;
	public boolean enableXenoTerrain = true;
	public boolean enableXenoSky = false;
	public boolean enableXenoEntities = false;
	public boolean enableXenoClouds = false;
	public boolean enableXenoWeather = false;

	public static final XenoConfig INSTANCE = new XenoConfig();
}
