package com.google.android.diskusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.io.File;
import java.util.Comparator;

public class FileSystemEntry implements Comparator<FileSystemEntry> {
  private static final Paint bg = new Paint();
  private static final Paint cursor_fg = new Paint();
  private static final Paint fg = new Paint();
  private static final Paint fill_bg = new Paint();
  private static final float ascent;
  private static final float descent;
  private static String n_bytes;
  private static String n_kilobytes; 
  private static String n_megabytes; 
  private static String n_megabytes10; 
  private static String n_megabytes100;
  private static String dir_name_size_num_dirs;
  private static String dir_empty;
  private static String dir_name_size;
  
  /**
   * Font size. Also accessed from FileSystemView.
   */
  static final float fontSize;
  
  /**
   * Width of one element. Setup from FileSystemView when geometry changes.
   */
  static int elementWidth;
  
  static {
    bg.setColor(Color.BLACK);
    bg.setStyle(Paint.Style.FILL);
    bg.setAlpha(150);
    fg.setColor(Color.WHITE);
    fg.setStyle(Paint.Style.STROKE);
    fill_bg.setColor(Color.WHITE);
    fill_bg.setStyle(Paint.Style.FILL);
    cursor_fg.setColor(Color.YELLOW);
    cursor_fg.setStyle(Paint.Style.STROKE);
    ascent = fg.ascent();
    descent = fg.descent();
    fontSize = descent - ascent;
  }

  // Object Fields:
  FileSystemEntry parent;
  FileSystemEntry[] children;
  String name;
  String sizeString;
  long size;

  /**
   * Constructor for file object.
   * @param parent parent directory object.
   * @param file corresponding File
   */
  FileSystemEntry(FileSystemEntry parent, File file) {
    this.parent = parent;
    this.name = file.getName();
    this.size = file.length();
  }
  
  /**
   * Constructor for root node.
   */
  public FileSystemEntry(FileSystemEntry[] children) {
    this.children = new FileSystemEntry[children.length];
    System.arraycopy(children, 0, this.children, 0, children.length);
    for (int i = 0; i < children.length; i++) {
      size += children[i].size;
      children[i].parent = this;
    }
  }

  /**
   * Constructor for directory object.
   * @param parent parent directory object.
   * @param file corresponding File object
   * @param depth current directory tree depth
   * @param maxdepth maximum directory tree depth
   */
  FileSystemEntry(FileSystemEntry parent, File file, int depth, int maxdepth) {
    this.parent = parent;
    this.name = file.getName();

    if (depth == maxdepth) {
      size = calculateSize(file);
      return;
    }

    File[] list = file.listFiles();
    if (list == null) return;

    FileSystemEntry[] children0 = new FileSystemEntry[list.length];
    int nchildren = 0;

    for (int i = 0; i < list.length; i++) {
      File child = list[i];

      if (isLink(child)) continue;
      if (isSpecial(child)) continue;

      FileSystemEntry c = null;

      if (child.isFile()) {
        c = new FileSystemEntry(this, child);
      } else {
        // directory
        c = new FileSystemEntry(this, child, depth + 1, maxdepth);
      }
      children0[nchildren++] = c;
      size += c.size;
    }

    if (nchildren != 0) {
      children = new FileSystemEntry[nchildren];
      System.arraycopy(children0, 0, children, 0, nchildren);
      java.util.Arrays.sort(children, this);
    }
  }
  

  public final int compare(FileSystemEntry aa, FileSystemEntry bb) {
    if (aa.size == bb.size)	return 0;
    return aa.size < bb.size ? 1 : -1;
  }

  /**
   * Find index of directChild in 'children' field of this entry. 
   * @param directChild
   * @return index of the directChild in 'children' field.
   */
  public final int getIndexOf(FileSystemEntry directChild) {
    FileSystemEntry[] children0 = children;
    int len = children0.length;
    int i;

    for (i = 0; i < len; i++) {
      if (children0[i] == directChild) break;
    }

    if (children0[i] != directChild) throw new RuntimeException("something broken");
    return i;
  }

  /**
   * Find entry which follows this entry in its the parent.
   * @return next entry in the same parent or this entry if there is no more entries
   */
  public final FileSystemEntry getNext() {
    int index = parent.getIndexOf(this);
    if (index + 1 == parent.children.length) return this;
    return parent.children[index + 1];
  }

  /**
   * Find entry which precedes this entry in its the parent.
   * @return previous entry in the same parent or this entry if the entry is first
   */
  public final FileSystemEntry getPrev() {
    int index = parent.getIndexOf(this);
    if (index == 0) return this;
    return parent.children[index - 1];
  }

  /**
   * Calculate size of the entry reading directory tree
   * @param file is file corresponding to this entry
   * @return size of entry in bytes
   */
  final long calculateSize(File file) {
    if (isLink(file)) return 0;

    if (file.isFile()) return file.length();

    File[] list = file.listFiles();
    if (list == null) return 0;
    long size = 0;

    for (int i = 0; i < list.length; i++)
      size += calculateSize(list[i]);
    return size;
  }

  private static boolean isLink(File file) {
    try {
      if (file.getCanonicalPath().equals(file.getPath())) return false;
    } catch(Throwable t) {}
    return true;
  }

  private static boolean isSpecial(File file) {
    String path = file.getPath();
    return (path.equals("/sys") || path.equals("/proc") || path.equals("/dev"));
  }

  private static void paint(long parent_size, FileSystemEntry[] entries,
      Canvas canvas, float xoffset, float yoffset, float yscale,
      long clipLeft, long clipRight, long clipTop, long clipBottom,
      int screenHeight) {

    FileSystemEntry children[] = entries;
    int len = children.length;
    long child_clipLeft = clipLeft - elementWidth;
    long child_clipRight = clipRight - elementWidth;
    long child_clipTop = clipTop;
    long child_clipBottom = clipBottom;
    float child_xoffset = xoffset + elementWidth;

    for (int i = 0; i < len; i++) {
      FileSystemEntry c = children[i];
      long csize = c.size;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;
      ///Log.d("DiskUsage", "child: child_clip_y0 = " + child_clip_y0);
      ///Log.d("DiskUsage", "child: child_clip_y1 = " + child_clip_y1);

      if (child_clipTop > csize) {

        child_clipTop -= csize;
        child_clipBottom -= csize;
        yoffset = bottom;
        continue;
      }

      if (child_clipBottom < 0) {
        ///Log.d("DiskUsage", "skipped rest starting from " + c.name);
        return;
      }

      FileSystemEntry[] cchildren = c.children;

      if (cchildren != null)
        FileSystemEntry.paint(c.size, cchildren, canvas,
            child_xoffset, yoffset, yscale,
            child_clipLeft, child_clipRight, child_clipTop, child_clipBottom, screenHeight);

      if (bottom - top < 2) {
        long all_size = 0;
        bottom += parent_size * yscale;
        canvas.drawRect(xoffset, top, child_xoffset, bottom, fill_bg);
        canvas.drawRect(xoffset, top, child_xoffset, bottom, fg);
        return;
      }

      if (clipLeft < elementWidth) {
          // FIXME
          float windowHeight0 = screenHeight;
          float fontSize0 = fontSize;
          float top0 = top < -1 ? -1 : top;
          float bottom0 = bottom > windowHeight0 ? windowHeight0 : bottom;
          
          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, bg);
          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, fg);

          if (bottom - top > fontSize0 * 2) {
            float pos = (top + bottom) * 0.5f;
            if (pos < fontSize0) {
              if (bottom > 2 * fontSize0) {
                pos = fontSize0;
              } else {
                pos = bottom - fontSize0;
              }
            } else if (pos > windowHeight0 - fontSize0) {
              if (top < windowHeight0 - 2 * fontSize0) {
                pos = windowHeight0 - fontSize0;
              } else {
                pos = top + fontSize0;
              }
            }
            float pos1 = pos - descent;
            float pos2 = pos - ascent;

            String sizeString0 = c.sizeString;
            if (sizeString0 == null) {
              c.sizeString = sizeString0 = calcSizeString(c.size);
            }
            canvas.drawText(c.name,  xoffset + 2, pos1, fg);
            canvas.drawText(sizeString0, xoffset + 2, pos2, fg);
          } else if (bottom - top > fontSize0)
            canvas.drawText(c.name, xoffset + 2, (top + bottom - ascent - descent) / 2, fg);
      }

      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }

  }

  final void paint(Canvas canvas, Rect bounds, Cursor cursor, long viewTop,
      float viewDepth, float yscale, int screenHeight) {
    // scale conversion:
    // window_y = yscale * world_y
    // world_y  = window_y / yscale

    // offset conversion:
    // window_y = yscale * (world_y  - rootOffset)
    // world_y  = window_y / yscale + rootOffset

    //viewTop = 23 * 1024 * 1024;
    //viewDepth = 0.3f;

    int viewLeft = (int)(viewDepth * elementWidth);

    // screen clip area to world conversion:
    long clipTop = (long)(bounds.top / yscale) + viewTop;
    long clipBottom = (long)(bounds.bottom / yscale) + viewTop;
    int clipLeft = bounds.left + viewLeft;
    int clipRight = bounds.right + viewLeft;
    float xoffset = -viewLeft;
    float yoffset = -viewTop * yscale;

    // X coords:
    // xoffset - screen position of current object on the screen
    // clip_x0, clip_x1 - clip area in coords of current object
    // screen_clip_x0 = xoffset + clip_x0
    // screen_clip_x1 = xoffset + clip_x1

    // Y coords:
    // yoffset - screen position of current object on the screen
    // clip_y0, clip_y1 - clip area in world coords relative to current object
    // screen_clip_y0 = yscale * (clip_y0 - elementOffset)
    // screen_clip_y1 = yscale * (clip_y1 - elementOffset)

    paint(size, children, canvas, xoffset, yoffset, yscale, clipLeft, clipRight,
        clipTop, clipBottom, screenHeight);

    // paint position
    float cursorLeft = cursor.depth * elementWidth + xoffset;
    float cursorTop = (cursor.top - viewTop) * yscale;
    float cursorRight = cursorLeft + elementWidth;
    float cursorBottom = cursorTop + cursor.position.size * yscale;
    canvas.drawRect(cursorLeft, cursorTop, cursorRight, cursorBottom, cursor_fg);
  }
  
  private static String calcSizeString(long size) {
    float sz = size;
    if (sz < 1024) return String.format(n_bytes, sz);
    if (sz < 1024 * 1024) return String.format(n_kilobytes, sz / 1024);
    if (sz < 1024 * 1024 * 10) return String.format(n_megabytes, sz / (1024 * 1024));
    if (sz < 1024 * 1024 * 200) return String.format(n_megabytes10, sz / (1024 * 1024));
    return String.format(n_megabytes100, sz / (1024 * 1024));
  }

  @Override
  public final String toString() {
    String sizeString0 = this.sizeString;
    if (sizeString0 == null) {
      sizeString0 = sizeString = calcSizeString(size);
    }
    if (children != null && children.length != 0)
      return String.format(dir_name_size_num_dirs, name, sizeString0, children.length);
    else if (size == 0) {
      return String.format(dir_empty, name);
    } else {
      return String.format(dir_name_size, name, sizeString0);
    }
  }

  public final String path() {
    if (parent == null) return "";
    return parent.path() + "/" + name;
  }

  /**
   * Find depth of 'entry' in current element.
   * @param entry
   * @return 1 for depth equal 1 and so on
   */
  public final int depth(FileSystemEntry entry) {
    int d = 0;
    FileSystemEntry root = this;

    while(entry != root) {
      entry = entry.parent;
      d++;
    }
    return d;
  }

  /**
   * Find and return entry on specified depth and offset in this entry used as root.
   * @param maxDepth
   * @param offset
   * @return nearest entry to the specified conditions
   */
  public final FileSystemEntry findEntry(int maxDepth, long offset) {
    long currOffset = 0;
    FileSystemEntry entry = this;
    FileSystemEntry[] children0 = children;
    // Log.d("DiskUsage", "Starting entry search at " + entry.name);

    for (int depth = 0; depth < maxDepth; depth++) {
      int nchildren = children0.length;
      // Log.d("DiskUsage", "  Entry = " + entry.name);
      for (int c = 0; c < nchildren; c++) {
        FileSystemEntry e = children0[c];
        long size = e.size;
        if (currOffset + size < offset) {
          currOffset += size;
          continue;
        }

        // found entry
        entry = e;
        children0 = e.children;
        if (children0 == null) return entry;
        break;
      }

    }
    return entry;
  }

  /**
   * Returns offset in bytes (world coordinates) from start of this
   * object to the start of 'cursor' object.
   * @param cursor
   * @return offset in bytes
   */
  public final long getOffset(FileSystemEntry cursor) {
    long offset = 0;
    FileSystemEntry dir;
    FileSystemEntry root = this;

    while (cursor != root) {
      dir = cursor.parent;
      FileSystemEntry[] children = dir.children;
      int len = children.length;

      for (int i = 0; i < len; i++) {
        FileSystemEntry e = children[i];
        if (e == cursor) break;
        offset += e.size;
      }
      cursor = dir;
    }
    return offset;
  }

  public final void remove() {
    FileSystemEntry[] children0 = parent.children;
    int len = children0.length;
    for (int i = 0; i < len; i++) {
      if (children0[i] != this) continue;
      
      // executed only once:
      parent.children = new FileSystemEntry[len - 1];
      System.arraycopy(children0, 0, parent.children, 0, i);
      System.arraycopy(children0, i + 1, parent.children, i, len - i - 1);
      //java.util.Arrays.sort(parent.children, this);
      
      FileSystemEntry parent0 = parent;
      
      while (parent0 != null) {
        parent0.size -= size;
        parent0.sizeString = null;
        java.util.Arrays.sort(parent0.children, this);
        parent0 = parent0.parent;
      }
      return;
    }
    throw new RuntimeException("child is not found: " + this);
  }

  public void insert(FileSystemEntry newEntry) {
    FileSystemEntry[] children0 = new FileSystemEntry[children.length + 1];
    System.arraycopy(children, 0, children0, 0, children.length);
    children0[children.length] = newEntry;
    children = children0;
    newEntry.parent = this;
    FileSystemEntry parent0 = this;
    
    while (parent0 != null) {
      java.util.Arrays.sort(children, this);
      parent0.size += newEntry.size;
      parent0.sizeString = null;
      parent0 = parent0.parent;
    }
  }

  /**
   * Walks through the path and finds the specified entry, null otherwise.
   */
  public FileSystemEntry getEntryByName(String path) {
    String[] pathElements = path.split("/");
    FileSystemEntry entry = this;
    
    outer:
      for (int i = 1; i < pathElements.length; i++) {
        String name = pathElements[i];
        FileSystemEntry[] children = entry.children;
        for (int j = 0; j < children.length; j++) {
          entry = children[j];
          if (name.equals(entry.name)) {
            continue outer;
          }
        }
        return null;
      }
    return entry;
  }

  static void setupStrings(Context context) {
    if (n_bytes != null) return;
    n_bytes = context.getString(R.string.n_bytes);
    n_kilobytes = context.getString(R.string.n_kilobytes);
    n_megabytes = context.getString(R.string.n_megabytes);
    n_megabytes10 = context.getString(R.string.n_megabytes10);
    n_megabytes100 = context.getString(R.string.n_megabytes100);
    dir_name_size_num_dirs = context.getString(R.string.dir_name_size_num_dirs);
    dir_empty = context.getString(R.string.dir_empty);
    dir_name_size = context.getString(R.string.dir_name_size);
  }
}