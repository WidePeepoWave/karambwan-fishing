package com.karambwanfishing;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KarambwanFishingTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KarambwanFishingPlugin.class);
		RuneLite.main(args);
	}
}