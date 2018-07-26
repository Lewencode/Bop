package com.sahdeepsingh.Bop.SongData;

import android.util.Log;

import com.sahdeepsingh.Bop.playerMain.Main;

import java.util.ArrayList;

public class Playlist {

    private long id;
    private String name;

    private ArrayList<Long> songs = new ArrayList<>();

    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Inserts a song on this Playlist.
     *
     * @param id Global song id.
     */
    public void add(long id) {
        if (! songs.contains(id))
            songs.add(id);
    }

    /**
     * Returns a list with all the songs inside this Playlist.
     * @return lol
     */
    public ArrayList<Long> getSongIds() {
        Log.e("wtf",String.valueOf(songs));

        return new ArrayList<>(songs);
    }
}