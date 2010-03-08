/*
 * Copyright (C) 2008 Romain Guy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xbmc.android.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.xbmc.api.object.ICoverArt;
import org.xbmc.api.type.MediaType;
import org.xbmc.api.type.ThumbSize;

import android.graphics.Bitmap;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public abstract class ImportUtilities {
	
	public static final double POSTER_AR = 1.4799154334038054968287526427061;

	private static final String TAG = "ImportUtilities";
    private static final String CACHE_DIRECTORY = "xbmc";
    private static final double MIN_FREE_SPACE = 15;

    public static File getCacheDirectory(String type, int size) {
    	StringBuilder sb = new StringBuilder(CACHE_DIRECTORY);
    	sb.append(type);
    	sb.append(ThumbSize.getDir(size));
        return IOUtilities.getExternalFile(sb.toString());
    }
    
    public static File getCacheFile(String type, int size, String name) {
    	StringBuilder sb = new StringBuilder(32);
    	sb.append(CACHE_DIRECTORY);
    	sb.append(type);
    	sb.append(ThumbSize.getDir(size));
    	sb.append("/");
    	sb.append(name);
    	return IOUtilities.getExternalFile(sb.toString());
    }

    public static Bitmap addCoverToCache(ICoverArt cover, Bitmap bitmap, int thumbSize) {
    	Bitmap sizeToReturn = null;
    	File cacheDirectory;
    	for (int currentThumbSize : ThumbSize.values()) {
    		try {
    			cacheDirectory = ensureCache(MediaType.getArtFolder(cover.getMediaType()), currentThumbSize);
    		} catch (IOException e) {
    			return null;
    		}
    		File coverFile = new File(cacheDirectory, Crc32.formatAsHexLowerCase(cover.getCrc()));
    		FileOutputStream out = null;
    		try {
    			out = new FileOutputStream(coverFile);
    			int width = 0;
    			int height = 0;
    			final double ar = ((double)bitmap.getWidth()) / ((double)bitmap.getHeight());
    			switch (cover.getMediaType()) {
    				default:
    				case MediaType.PICTURES:
    				case MediaType.MUSIC:
    					if (ar < 1) {
    						width = ThumbSize.getPixel(currentThumbSize);
    						height = (int)(width / ar); 
    					} else {
    						height = ThumbSize.getPixel(currentThumbSize);
    						width = (int)(height * ar); 
    					}
    					break;
    				case MediaType.VIDEO:
    					if (ar > 0.98 && ar < 1.02) { 	// square
    						Log.i(TAG, "Format: SQUARE");
    						width = ThumbSize.getPixel(currentThumbSize);
    						height = ThumbSize.getPixel(currentThumbSize);
    					} else if (ar < 1) {			// portrait
    						Log.i(TAG, "Format: PORTRAIT");
    						width = ThumbSize.getPixel(currentThumbSize);
    						final int ph = (int)(POSTER_AR * width);
    						height = (int)(width / ar); 
    						if (height < ph) { 
    							height = ph;
    							width = (int)(height * ar);
    						}
    					} else if (ar < 2) {			// landscape 16:9
    						Log.i(TAG, "Format: LANDSCAPE 16:9");
    						height = ThumbSize.getPixel(currentThumbSize);
    						width = (int)(height * ar); 
    					} else if (ar > 5) {			// wide banner
    						Log.i(TAG, "Format: BANNER");
    						width = ThumbSize.getPixel(currentThumbSize) * 2;
    						height = (int)(width / ar); 
    					} else {						// anything between wide banner and landscape 16:9
    						Log.i(TAG, "Format: BIZARRE");
    						height = ThumbSize.getPixel(currentThumbSize);
    						width = (int)(height * ar); 
    					}
    					break;
    			}
    			Log.i(TAG, "Resizing to " + width + "x" + height);
    			final Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
    			resized.compress(Bitmap.CompressFormat.PNG, 100, out);
    			if (thumbSize == currentThumbSize) {
    				sizeToReturn = resized;
    			}
    		} catch (FileNotFoundException e) {
    			return null;
    		} finally {
    			IOUtilities.closeStream(out);
    		}
    	}
        return sizeToReturn;
    }
    
    /**
     * Returns number of free bytes on the SD card.
     * @return Number of free bytes on the SD card.
     */
    public static long freeSpace() {
    	StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }
    
    /**
     * Returns total size of SD card in bytes.
     * @return Total size of SD card in bytes.
     */
    public static long totalSpace() {
    	StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
    	long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
    	return totalBlocks * blockSize;
    }
    
    public static String assertSdCard() {
    	if (!android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
    		return "Your SD card is not mounted. You'll need it for caching thumbs.";
    	}
    	if (freePercentage() < MIN_FREE_SPACE) {
    		return "You need to have more than " + MIN_FREE_SPACE + "% of free space on your SD card.";
    	}
    	return null;
    }
    
    
    /**
     * Returns free space in percent.
     * @return Free space in percent.
     */
    public static double freePercentage() {
    	StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long availableBlocks = stat.getAvailableBlocks();
        long totalBlocks = stat.getBlockCount();
        return (double)availableBlocks / (double)totalBlocks * 100;
    }

    private static File ensureCache(String type, int size) throws IOException {
        File cacheDirectory = getCacheDirectory(type, size);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
            new File(cacheDirectory, ".nomedia").createNewFile();
        }
        return cacheDirectory;
    }
    
    public static void purgeCache() {
    	final int size[] = ThumbSize.values();
    	final int[] mediaTypes = MediaType.getTypes();
    	for (int i = 0; i < mediaTypes.length; i++) {
    		String folder = MediaType.getArtFolder(mediaTypes[i]);
    		for (int j = 0; j < size.length; j++) {
    			File cacheDirectory = getCacheDirectory(folder, size[j]);
    			if (cacheDirectory.exists() && cacheDirectory.isDirectory()) {
    				for (File file : cacheDirectory.listFiles()) {
    					file.delete();
    				}
    			}
    		}
    	}
    }
}
