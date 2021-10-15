/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.cql.library;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;

import com.ibm.cohort.cql.util.HadoopConfigUtil;

public class HadoopBasedCqlLibraryProvider implements CqlLibraryProvider {
    
    private Path directory;
    
    public HadoopBasedCqlLibraryProvider(Path directory) {
        this.directory = directory;
    }
    
    @Override
    public Collection<CqlLibraryDescriptor> listLibraries() {
        try {
            FileSystem fileSystem = HadoopConfigUtil.getFileSystemForPath(directory);
            RemoteIterator<LocatedFileStatus> fileStatusIterator = fileSystem.listFiles(directory, false);
            Set<CqlLibraryDescriptor> retVal = new HashSet<>();
            while(fileStatusIterator.hasNext()) {
                LocatedFileStatus fileStatus = fileStatusIterator.next();
                String name = fileStatus.getPath().getName();
                CqlLibraryDescriptor libraryDescriptor = CqlLibraryHelpers.filenameToLibraryDescriptor(name);
                if (libraryDescriptor != null) {
                    retVal.add(libraryDescriptor);
                }
            }
            return retVal;
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CqlLibrary getLibrary(CqlLibraryDescriptor libraryDescriptor) {
        CqlLibrary library = null;
        
        try {
            FileSystem fileSystem = HadoopConfigUtil.getFileSystemForPath(directory);
            Path path = new Path(directory, new Path(CqlLibraryHelpers.libraryDescriptorToFilename(libraryDescriptor)));
            if (fileSystem.exists(path)) {
                try (FSDataInputStream f = fileSystem.open(path)) {
                    library = new CqlLibrary()
                            .setDescriptor(libraryDescriptor)
                            .setContent(IOUtils.toString(f, Charset.defaultCharset()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize library " + libraryDescriptor, e);
        }

        return library;
    }
}
