/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */
/*Heavily influenced by the extension for Kotlin Language Server which is:
 * Copyright (c) 2016 George Fraser
 * Copyright (c) 2018 fwcd
 */
/*Also based on the vscode-mock-debug.*/
'use strict';

import { workspace, ExtensionContext } from 'vscode';

import {
	LanguageClient,
	LanguageClientOptions,
	ServerOptions
} from 'vscode-languageclient';

import * as path from 'path';
import * as vscode from 'vscode';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    let serverPath = path.resolve(context.extensionPath, "nb-java-lsp-server", "bin", "nb-java-lsp-server");

    let serverOptions: ServerOptions = {
        command: serverPath,
        options: { cwd: workspace.rootPath }
    }

    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: ['java'],
        synchronize: {
            configurationSection: 'java',
            fileEvents: [
                workspace.createFileSystemWatcher('**/*.java')
            ]
        },
        outputChannelName: 'Java',
        revealOutputChannelOn: 4 // never
    }

    // Create the language client and start the client.
    client = new LanguageClient(
            'java',
            'NetBeans Java',
            serverOptions,
            clientOptions
    );

    // Start the client. This will also launch the server
    client.start();

    //register debugger:
    let configProvider = new NetBeansConfigurationProvider();
    context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('java', configProvider));

    let debugDescriptionFactory = new NetBeansDebugAdapterDescriptionFactory();
    context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory('java', debugDescriptionFactory));
}

export function deactivate(): Thenable<void> {
	if (!client) {
		return undefined;
	}
	return client.stop();
}

class NetBeansDebugAdapterDescriptionFactory implements vscode.DebugAdapterDescriptorFactory {
    createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable | undefined): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
        return new vscode.DebugAdapterServer(10001);
    }

}


class NetBeansConfigurationProvider implements vscode.DebugConfigurationProvider {

    resolveDebugConfiguration(folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
        config.mainClass = config.program;
        config.classPaths = ['any'];

        return config;
    }
}
