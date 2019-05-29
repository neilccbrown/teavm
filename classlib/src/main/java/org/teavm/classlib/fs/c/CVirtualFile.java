/*
 *  Copyright 2019 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.fs.c;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.classlib.fs.VirtualFile;
import org.teavm.classlib.fs.VirtualFileAccessor;
import org.teavm.classlib.impl.c.StringList;
import org.teavm.interop.Address;

public class CVirtualFile implements VirtualFile {
    CFileSystem fileSystem;
    String path;

    public CVirtualFile(CFileSystem fileSystem, String path) {
        this.fileSystem = fileSystem;
        this.path = path;
    }

    @Override
    public String getName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public boolean isDirectory() {
        char[] chars = path.toCharArray();
        return CFileSystem.isDir(chars, chars.length);
    }

    @Override
    public boolean isFile() {
        char[] chars = path.toCharArray();
        return CFileSystem.isFile(chars, chars.length);
    }

    @Override
    public VirtualFile[] listFiles() {
        char[] chars = path.toCharArray();
        StringList list = CFileSystem.listFiles(chars, chars.length);
        if (list == null) {
            return null;
        }

        List<VirtualFile> files = new ArrayList<>();
        while (list != null) {
            if (list.data != null) {
                chars = new char[list.length];
                Address data = list.data.toAddress();
                for (int i = 0; i < chars.length; ++i) {
                    chars[i] = data.add(i * 2).getChar();
                }
                CFileSystem.free(data);
                String name = new String(chars);
                if (!name.equals(".") && !name.equals("..")) {
                    files.add(fileSystem.getByPath(path + "/" + name));
                }
            }
            StringList next = list.next;
            CFileSystem.free(list.toAddress());
            list = next;
        }
        Collections.reverse(files);

        return files.toArray(new VirtualFile[0]);
    }

    @Override
    public VirtualFile getChildFile(String fileName) {
        String childPath = path + "/" + fileName;
        return fileSystem.getByPath(childPath);
    }

    @Override
    public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
        char[] chars = path.toCharArray();
        int mode = 0;
        if (readable) {
            mode |= 1;
        }
        if (writable) {
            mode |= 2;
        }
        if (append) {
            mode |= 4;
        }
        long file = CFileSystem.open(chars, chars.length, mode);
        if (file == 0) {
            return null;
        }
        return new CVirtualFileAccessor(file, append ? -1 : 0);
    }

    @Override
    public VirtualFile createFile(String fileName) throws IOException {
        String newPath = path + "/" + fileName;
        char[] chars = newPath.toCharArray();
        int result = CFileSystem.createFile(chars, chars.length);
        switch (result) {
            case 0:
                return fileSystem.getByPath(newPath);
            case 1:
                return null;
            default:
                throw new IOException("Could not create file " + fileName);
        }
    }

    @Override
    public VirtualFile createDirectory(String fileName) {
        String newPath = path + "/" + fileName;
        char[] chars = newPath.toCharArray();
        if (!CFileSystem.createDirectory(chars, chars.length)) {
            return null;
        }
        return fileSystem.getByPath(newPath);
    }

    @Override
    public boolean delete() {
        char[] chars = path.toCharArray();
        return CFileSystem.delete(chars, chars.length);
    }

    @Override
    public boolean adopt(VirtualFile file, String fileName) {
        char[] chars = ((CVirtualFile) file).path.toCharArray();
        String newPath = path + "/" + fileName;
        char[] newPathChars = newPath.toCharArray();
        return CFileSystem.rename(chars, chars.length, newPathChars, newPathChars.length);
    }

    @Override
    public boolean canRead() {
        char[] chars = path.toCharArray();
        return CFileSystem.canRead(chars, chars.length);
    }

    @Override
    public boolean canWrite() {
        char[] chars = path.toCharArray();
        return CFileSystem.canWrite(chars, chars.length);
    }

    @Override
    public long lastModified() {
        char[] chars = path.toCharArray();
        return CFileSystem.lastModified(chars, chars.length);
    }

    @Override
    public void setLastModified(long lastModified) {
    }

    @Override
    public void setReadOnly(boolean readOnly) {
    }

    @Override
    public int length() {
        char[] chars = path.toCharArray();
        return CFileSystem.length(chars, chars.length);
    }
}
