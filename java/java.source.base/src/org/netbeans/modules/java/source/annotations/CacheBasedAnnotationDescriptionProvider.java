/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.java.source.annotations;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=AnnotationsDescriptionProvider.class, position=1000)
public class CacheBasedAnnotationDescriptionProvider implements AnnotationsDescriptionProvider {

    private static final Logger LOG = Logger.getLogger(CacheBasedAnnotationDescriptionProvider.class.getName());

    @Override
    public FileObject annotationDescriptionForRoot(FileObject root) {
        return annotationDescriptionForRoot(root, false);
    }

    public static FileObject overrideCacheDirForTests;
    
    private static final String ANNOTATIONS_FOLDER = "annotations";

    public static FileObject annotationDescriptionForRoot(FileObject root, boolean create) {
        if (overrideCacheDirForTests != null) return overrideCacheDirForTests;
        
        Project prj = FileOwnerQuery.getOwner(root);
        FileObject nbProject;

        if (prj == null || (nbProject = prj.getProjectDirectory().getFileObject("nbproject")) == null) {
            //currently, only supporting projects
            return null;
        }

        try {
            return create ? FileUtil.createFolder(nbProject, ANNOTATIONS_FOLDER) : nbProject.getFileObject(ANNOTATIONS_FOLDER);
        } catch (IOException ex) {
            LOG.log(Level.FINE, null, ex);
            return null;
        }
    }

}
