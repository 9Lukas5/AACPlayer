/**
 * Copyright (C) 2017 Lukas Wiest
 * v1.0.1
 */
package de.wiest_lukas.lib;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;
import javax.sound.sampled.*;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.*;

/**
 * This library plays AAC audio files, using the JAAD decoder. To use this lib,
 * you need to have the JAAD library jar, too.
 * 
 * @author Lukas Wiest
 */
public class AACPlayer
{
    private boolean loop;
    private boolean repeat;
    private Thread  playback;
    private boolean paused;
    private File[]  files;

    /**
     * creates a new Instance of AACPlayer with a set of Files to be played back.
     * @param files Filelist to playback.
     */
    public AACPlayer(File[] files)
    {
        loop        = false;
        repeat      = false;
        paused      = false;

        List<File> validFiles = new LinkedList<>();

        for (File temp: files)
        {
            try
            {
                MP4Container cont = new MP4Container(new RandomAccessFile(temp, "r"));
                Movie movie = cont.getMovie();
                List<Track> includedTracks = movie.getTracks();

                if (!includedTracks.isEmpty())
                    validFiles.add(temp);
                else
                    System.err.println("no tracks found in " + temp.getName() + ". Skipping this one.");
            }
            catch(IOException e)
            {
                System.err.println("FileNotFound, skipping " + temp.getName());
            }
        }

        this.files = new File[validFiles.size()];
        for (int i=0; i < validFiles.size(); i++)
            this.files[i] = (File) validFiles.get(i);
    }

    /**
     * Player with only one File in List.
     * @param file 
     */
    public AACPlayer(File file)
    {
        this (new File[] {file});
    }

    /**
     * Instances a new Player with one File, Path given as String.
     * @param pathToFile 
     */
    public AACPlayer(String pathToFile)
    {
        this (new File (pathToFile));
    }

    private void initThread()
    {
        playback = new Thread()
        {
            @Override
            public void run()
            {
                // local vars
                byte[]          b;
                AudioTrack      track;
                AudioFormat     af;
                SourceDataLine  line;
                Decoder         dec;
                Frame           frame;
                SampleBuffer    buf;
                int             currentTrack;
                MP4Container    cont;
                Movie           movie;

                try
                {
                    for (currentTrack = 0; currentTrack < files.length; currentTrack++)
                    {
                        cont    = new MP4Container(new RandomAccessFile(files[currentTrack], "r"));
                        movie   = cont.getMovie();
                        track   = (AudioTrack) movie.getTracks().get(0);
                        af      = new AudioFormat(track.getSampleRate(), track.getSampleSize(), track.getChannelCount(), true, true);
                        line    = AudioSystem.getSourceDataLine(af);
                        line.open();
                        line.start();

                        dec     = new Decoder(track.getDecoderSpecificInfo());

                        buf = new SampleBuffer();
                        while(track.hasMoreFrames())
                        {
                            frame = track.readNextFrame();
                            dec.decodeFrame(frame.getData(), buf);
                            b = buf.getData();
                            line.write(b, 0, b.length);

                            while (paused)
                            {
                                Thread.sleep(500);

                                if (Thread.interrupted())
                                {
                                    line.close();
                                    return;
                                }
                            }

                            if (Thread.interrupted())
                            {
                                line.close();
                                return;
                            }
                        }

                        line.close();

                        if (loop)
                            currentTrack--;
                        else if (repeat && (currentTrack == files.length -1))
                            currentTrack = -1;
                    }
                }
                catch (LineUnavailableException | IOException | InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * Starts Playback of given File(s) with the first file.
     */
    public void play()
    {
        if (playback != null && playback.isAlive())
        {
            System.err.println("it plays yet, before you start again, stop it.");
            return;
        }

        initThread();
        playback.start();
    }

    /**
     * Stops playback
     */
    public void stop()
    {
        playback.interrupt();
    }

    /**
     * Pauses playback.
     * Can be resumed at paused position with method resume.
     */
    public void pause()
    {
        paused = true;
    }

    /**
     * resumes playback on paused position.
     * If playback isn't paused, nothing happens.
     */
    public void resume()
    {
        paused = false;
    }

    /**
     * Enales loop of current file.
     */
    public void enableLoop()
    {
        loop = true;
    }

    /**
     * Disables loop of current file.
     */
    public void disableLoop()
    {
        loop = false;
    }

    /**
     * Enabled repeated playback of whole file list.
     */
    public void enableRepeat()
    {
        repeat = true;
    }

    /**
     * Disables repeated playback of whole filelist.
     */
    public void disableRepeat()
    {
        repeat = false;
    }

    /**
     * Checks the state of playback.
     * @return true if playback thread is still alive
     */
    public boolean isPlaying()
    {
        if (playback != null)
            return playback.isAlive();
        else
            return false;
    }

}
