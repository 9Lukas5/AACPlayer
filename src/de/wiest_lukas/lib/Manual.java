package de.wiest_lukas.lib;

/**
 * Short textmessage on console, if someone tries to start this jar stand-alone,
 * telling him/her this is a library.
 */
public class Manual
{
    public static void main(String[] args)
    {
        System.out.println("This is a library for use in Java programs.\n"
                + "It uses the JAAD Decoder to decode AAC audio files.\n"
                + "Usage is described in the JavaDoc.");
    }
}
