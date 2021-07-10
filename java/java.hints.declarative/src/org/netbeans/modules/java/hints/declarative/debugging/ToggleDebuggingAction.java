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
package org.netbeans.modules.java.hints.declarative.debugging;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.WeakSet;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;

/**
 *
 * @author lahvac
 */
public class ToggleDebuggingAction extends BaseAction implements Presenter.Toolbar, ContextAwareAction {

    public static final String toggleDebuggingAction = "toggle-debugging-action";
    static final long serialVersionUID = 0L;

    private static final Map<Document, DebuggingPreview> hintDocument2DebuggingPreview = new WeakHashMap<>();
    static final Set<ToggleDebuggingAction> actions = Collections.synchronizedSet(new WeakSet<>());

    private JEditorPane pane;

    private JToggleButton toggleButton;

    public ToggleDebuggingAction() {
        super(toggleDebuggingAction);
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon("org/netbeans/modules/java/hints/declarative/resources/toggle-debugging.png", false)); //NOI18N
        putValue("noIconInMenu", Boolean.TRUE); // NOI18N
    }
    
    public ToggleDebuggingAction(JEditorPane pane) {
        this();
        
        assert (pane != null);
        this.pane = pane;
        actions.add(this);
        updateState();
    }

    private void updateState() {
        if (pane != null && toggleButton != null) {
            boolean debugging;
            synchronized (hintDocument2DebuggingPreview) {
                debugging = hintDocument2DebuggingPreview.containsKey(pane.getDocument());
            }
            toggleButton.setSelected(debugging);
            toggleButton.setContentAreaFilled(debugging);
            toggleButton.setBorderPainted(debugging);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target != null && !Boolean.TRUE.equals(target.getClientProperty("AsTextField"))) {
            Document doc = target.getDocument();
            synchronized (hintDocument2DebuggingPreview) {
                if (hintDocument2DebuggingPreview.containsKey(doc)) {
                    DebuggingPreview preview = hintDocument2DebuggingPreview.remove(doc);
                    if (preview != null) {
                        preview.getDiffComponent().close();
                    }
                } else {
                    Document javaDoc = DocumentTracker.lastJavaDoc != null ? DocumentTracker.lastJavaDoc.get() : null;
                    hintDocument2DebuggingPreview.put(doc, javaDoc != null ? new DebuggingPreview(javaDoc, NbEditorUtilities.getFileObject(doc).getNameExt()) : null);
                    //TODO: refresh hints
                }
            }

            updateStateForAll();
        }
    }

    public static void enableDebugging(Document hintDocument) {
        assert SwingUtilities.isEventDispatchThread();

        synchronized (hintDocument2DebuggingPreview) {
            if (hintDocument2DebuggingPreview.containsKey(hintDocument)) {
                return ;
            }

            Document javaDoc = DocumentTracker.lastJavaDoc != null ? DocumentTracker.lastJavaDoc.get() : null;

            hintDocument2DebuggingPreview.put(hintDocument, javaDoc != null ? new DebuggingPreview(javaDoc, NbEditorUtilities.getFileObject(hintDocument).getNameExt()) : null);
        }

        updateStateForAll();
    }

    private static void updateStateForAll() {
        assert SwingUtilities.isEventDispatchThread();

        for (ToggleDebuggingAction a : actions) {
            a.updateState();
        }
    }

    public static DebuggingPreview getPreviewFor(Document hintDocument) {
        synchronized (hintDocument2DebuggingPreview) {
            return hintDocument2DebuggingPreview.get(hintDocument);
        }
    }

    public static Iterable<Document> getHintDocumentsWithDebugging() {
        synchronized (hintDocument2DebuggingPreview) {
            return new ArrayList<>(hintDocument2DebuggingPreview.keySet());
        }
    }

    @Override
    public Component getToolbarPresenter() {
        toggleButton = new JToggleButton();
        toggleButton.putClientProperty("hideActionText", Boolean.TRUE); //NOI18N
        toggleButton.setIcon((Icon) getValue(SMALL_ICON));
        toggleButton.setAction(this); // this will make hard ref to button => check GC
        return toggleButton;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        JEditorPane pane = actionContext.lookup(JEditorPane.class);
        if (pane != null) {
            return new ToggleDebuggingAction(pane);
        }
        return this;
    }

    @Override
    protected Class getShortDescriptionBundleClass() {
        return ToggleDebuggingAction.class;
    }

}
