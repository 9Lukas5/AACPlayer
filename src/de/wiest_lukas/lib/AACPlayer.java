/**
 * Copyright (C) 2017 Lukas Wiest
 * v1.2.0
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
    private boolean loop;       // controls the looping behaviour of the current file
    private boolean repeat;     // controls the looping behaviour of the complete filelist
    private Thread  playback;   // in this thread the actual playback will happen
    private boolean paused;     // get's checked regularly from the playback thread and controls it pause if set true
    private boolean muted;      // if set, the playback goes on, but doesn't write the read bytes to the AudioSystem
    private boolean interrupted;// needed for Windows, as of broken SourceDataLine interrupt clearing on write method
    private File[]  files;      // file list that the playback will play down

    /**
     * creates a new Instance of AACPlayer with a set of Files to be played back.
     * @param files Filelist to playback.
     */
    public AACPlayer(File[] files)
    {
        // init these fields
        loop        = false;
        repeat      = false;
        paused      = false;
        muted       = false;
        interrupted = false;

        // LinkedList for all given files, which are a valid audiofile
        List<File> validFiles = new LinkedList<>();

        for (File temp: files)
        {
            try
            {
                MP4Container cont = new MP4Container(new RandomAccessFile(temp, "r"));  // open container with random access
                Movie movie = cont.getMovie();                                          // get the content from the container
                List<Track> includedTracks = movie.getTracks();                         // get the tracks

                if (!includedTracks.isEmpty())                                          // if track isn't empty, add it to the filelist
                    validFiles.add(temp);
                else
                    System.err.println("no tracks found in " + temp.getName() + ". Skipping this one.");
            }
            catch(IOException e)
            {
                System.err.println("FileNotFound, skipping " + temp.getName());
            }
        }

        this.files = new File[validFiles.size()];       // initialize the filearray with the size of the found valid files
        for (int i=0; i < validFiles.size(); i++)       // and put them in
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
        interrupted = false;    // needs to be reset, if the Thread is recreated, too.
        playback = new Thread()
        {
            @Override
            public void run()
            {
                // local vars
                byte[]          b;              // array for the actual audio Data during the playback
                AudioTrack      track;          // track we are playing atm
                AudioFormat     af;             // the track's format
                SourceDataLine  line;           // the line we'll use the get our audio to the speaker's
                Decoder         dec;            // decoder to get the audio bytes
                Frame           frame;          //
                SampleBuffer    buf;            //
                int             currentTrack;   // index of current track from playlist
                MP4Container    cont;           // container to open the current track with
                Movie           movie;          // and get the content from the container

                try
                {
                    // for-next loop to play each titel from the playlist once
                    for (currentTrack = 0; currentTrack < files.length; currentTrack++)
                    {
                        cont    = new MP4Container(new RandomAccessFile(files[currentTrack], "r")); // open titel with random access
                        movie   = cont.getMovie();                          // get content from container,
                        track   = (AudioTrack) movie.getTracks().get(0);    // grab first track and set the audioformat
                        af      = new AudioFormat(track.getSampleRate(), track.getSampleSize(), track.getChannelCount(), true, true);
                        line    = AudioSystem.getSourceDataLine(af);        // get a DataLine from the AudioSystem
                        line.open();                                        // open and
                        line.start();                                       // start it

                        dec     = new Decoder(track.getDecoderSpecificInfo());

                        buf = new SampleBuffer();

                        playback:
                        while(!isInterrupted() && track.hasMoreFrames())    // while we have frames left
                        {
                            frame = track.readNextFrame();                  // read next frame,
                            dec.decodeFrame(frame.getData(), buf);          // decode it and put into the buffer
                            b = buf.getData();                              // write the frame data from the buffer to our byte-array
                            if (!muted)                                     // only write the sound to the line if we aren't muted
                                line.write(b, 0, b.length);                 // and from there write the byte array into our open AudioSystem DataLine

                            if (interrupted && !isInterrupted())            // check for interrupt clearing source Data Line system
                            {
                                System.err.println("[LOG] - E - Source Data Line on your system clears interrupted flag!");
                                interrupt();                                // and correct it if necessary
                            }

                            while (paused)                                  // check if we should pause
                            {
                                Thread.sleep(500);                          // if yes, stay half a second

                                if (isInterrupted())                        // check if we should stop possibly
                                    break playback;                         // if yes, break playback loop
                            }
                        }

                        line.close();           // after titel is over or playback loop got broken, close line

                        if (Thread.interrupted())
                            return;             // if interrupt is set, clear it and leave

                        if (loop)               // if we should loop current titel, set currentTrack -1,
                            currentTrack--;     // as on bottom of for-next it get's +1 and so the same titel get's played again
                        else if (repeat && (currentTrack == files.length -1)) // else check if we are at the end of the playlist
                            currentTrack = -1;  // and should repeat the whole list. If so, set currentTrack -1, so it get's 0 on for-next bottom
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
        // check if playback is still running
        if (playback != null && playback.isAlive())
        {
            System.err.println("it plays yet, before you start again, stop it.");
            return;
        }

        initThread();           // init new Thread
        playback.start();       // and start it
    }

    /**
     * Stops playback
     */
    public void stop()
    {
        if (playback != null)       // avoid null Pointer exception, if someone stops before playing
            playback.interrupt();   // tell playback to stop
        interrupted = true;         // set own interrupted flag
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
     * mutes playback (in background the file is still processed, but the audio data not given
     * to the AudioSystem.
     * 
     * @return true if player is muted after call
     */
    public boolean toggleMute()
    {
        this.muted = !this.muted;
        return this.isMuted();
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

    /**
     * Returns the mute state
     * @return true if player is muted right now
     */
    public boolean isMuted()
    {
        return this.muted;
    }

}
