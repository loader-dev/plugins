/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.metronome;

import com.google.inject.Provides;
import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Preferences;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "Metronome",
	enabledByDefault = false,
	description = "Play sounds in a customisable pattern",
	tags = {"skilling", "tick", "timers"},
	type = PluginType.MISCELLANEOUS
)
@Slf4j
public class MetronomePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MetronomePluginConfiguration config;

	private int tickCounter = 0;
	private int tockCounter = 0;
	private Clip tickClip;
	private Clip tockClip;

	@Provides
	MetronomePluginConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MetronomePluginConfiguration.class);
	}

	private Clip GetAudioClip(String path)
	{
		File audioFile = new File(path);
		if (!audioFile.exists())
		{
			return null;
		}

		try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile))
		{
			Clip audioClip = AudioSystem.getClip();
			audioClip.open(audioStream);
			FloatControl gainControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
			float gainValue = (((float) config.volume()) * 40f / 100f) - 35f;
			gainControl.setValue(gainValue);

			return audioClip;
		}
		catch (IOException | LineUnavailableException | UnsupportedAudioFileException e)
		{
			log.warn("Error opening audiostream from " + audioFile, e);
			return null;
		}
	}

	@Override
	protected void startUp()
	{
		tickClip = GetAudioClip(config.tickPath());
		tockClip = GetAudioClip(config.tockPath());
	}

	@Override
	protected void shutDown()
	{
		tickCounter = 0;
		tockCounter = 0;

		if (tickClip != null)
		{
			tickClip.close();
		}
		if (tockClip != null)
		{
			tockClip.close();
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("metronome"))
		{
			return;
		}

		if (event.getKey().equals("volume"))
		{
			float gainValue = (((float) config.volume()) * 40f / 100f) - 35f;
			FloatControl gainControlTick = (FloatControl) tickClip.getControl(FloatControl.Type.MASTER_GAIN);
			gainControlTick.setValue(gainValue);

			FloatControl gainControlTock = (FloatControl) tockClip.getControl(FloatControl.Type.MASTER_GAIN);
			gainControlTock.setValue(gainValue);
		}
		if (event.getKey().equals("tickSoundFilePath"))
		{
			if (tickClip != null)
			{
				tickClip.close();
			}
			tickClip = GetAudioClip(config.tickPath());
		}
		if (event.getKey().equals("tockSoundFilePath"))
		{
			if (tockClip != null)
			{
				tockClip.close();
			}
			tockClip = GetAudioClip(config.tockPath());
		}
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		if (config.tickCount() == 0)
		{
			return;
		}

		if ((++tickCounter + config.tickOffset()) % config.tickCount() == 0)
		{
			// As playSoundEffect only uses the volume argument when the in-game volume isn't muted, sound effect volume
			// needs to be set to the value desired for ticks or tocks and afterwards reset to the previous value.
			Preferences preferences = client.getPreferences();
			int previousVolume = preferences.getSoundEffectsVolume();

			if ((config.tockVolume() > 0 && config.tockNumber() > 0) && ++tockCounter % config.tockNumber() == 0)
			{
				if (tockClip == null)
				{
					preferences.setSoundEffectsVolume(config.tockVolume());
					client.playSoundEffect(SoundEffectID.GE_DECREMENT_PLOP, config.tockVolume());
				}
				else
				{
					if (tockClip.isRunning())
					{
						tockClip.stop();
					}
					tockClip.setFramePosition(0);
					tockClip.start();
				}
			}
			else if (config.tickVolume() > 0)
			{
				if (tickClip == null)
				{
					preferences.setSoundEffectsVolume(config.tickVolume());
					client.playSoundEffect(SoundEffectID.GE_INCREMENT_PLOP, config.tickVolume());
				}
				else
				{
					if (tickClip.isRunning())
					{
						tickClip.stop();
					}
					tickClip.setFramePosition(0);
					tickClip.start();
				}
			}

			preferences.setSoundEffectsVolume(previousVolume);
		}
	}
}
