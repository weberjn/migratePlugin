/*

	Copyright 2016 Jürgen Weber

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/


package de.jwi.jspwiki.migrate;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.ecyrd.jspwiki.PageManager;
import com.ecyrd.jspwiki.WikiContext;
import com.ecyrd.jspwiki.WikiEngine;
import com.ecyrd.jspwiki.WikiPage;
import com.ecyrd.jspwiki.attachment.Attachment;
import com.ecyrd.jspwiki.attachment.AttachmentManager;
import com.ecyrd.jspwiki.event.WikiEngineEvent;
import com.ecyrd.jspwiki.event.WikiEvent;
import com.ecyrd.jspwiki.event.WikiEventListener;
import com.ecyrd.jspwiki.event.WikiEventUtils;
import com.ecyrd.jspwiki.plugin.InitializablePlugin;
import com.ecyrd.jspwiki.plugin.PluginException;
import com.ecyrd.jspwiki.plugin.WikiPlugin;
import com.ecyrd.jspwiki.providers.BasicAttachmentProvider;
import com.ecyrd.jspwiki.providers.VersioningFileProvider;
import com.ecyrd.jspwiki.providers.WikiAttachmentProvider;
import com.ecyrd.jspwiki.providers.WikiPageProvider;




public class Migrator implements WikiPlugin,InitializablePlugin,WikiEventListener
{
	private static Map<String, Migrator> weakReferencePoisoner = new HashMap<String, Migrator>();
	private Properties wikiProperties;
	private WikiEngine engine;
	
	
	public void initialize(WikiEngine engine) throws PluginException
	{
		this.engine = engine;
		wikiProperties = engine.getWikiProperties();
		
		boolean goForIt = "true".equals(wikiProperties.getProperty("de.jwi.jspwiki.git.migrate.Migrator.goForIt"));

		if (!goForIt)
			return;

		System.out.println("initialize()");

		weakReferencePoisoner.put("poison", this);
		
		WikiEventUtils.addWikiEventListener(engine, WikiEngineEvent.INITIALIZED, this);
	
	}

	public String execute(WikiContext context, @SuppressWarnings("rawtypes") Map map) throws PluginException
	{
		if (true)
		{
			throw new PluginException("never directly call this");
		}
		return null;
	}

	public void actionPerformed(WikiEvent event)
	{
		System.out.println("Migrator.actionPerformed()");
		
		Properties p = new Properties(wikiProperties);
		//p.put(GitFileProvider.PROP_PAGEDIR, wikiProperties.getProperty("de.jwi.jspwiki.git.migrate.Migrator.pageDir"));
		
		try
		{
			migrate(p);
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		
	}

	private void migrate(Properties properties) throws Exception
	{
		VersioningFileProvider versioningFileProvider = new VersioningFileProvider();
		versioningFileProvider.initialize(engine, properties);
		
		BasicAttachmentProvider basicAttachmentProvider = new BasicAttachmentProvider();
		basicAttachmentProvider.initialize(engine, properties);
		
		PageManager pageManager = engine.getPageManager();
		@SuppressWarnings("unchecked")
		Collection<WikiPage> allPages = pageManager.getAllPages();
		
		Iterator<WikiPage> it = allPages.iterator();
		while (it.hasNext())
		{
			WikiPage p = it.next();
			
			String pageName = p.getName();
			
			WikiPage pv = pageManager.getPageInfo(pageName, WikiPageProvider.LATEST_VERSION);
			
			int cv = pv.getVersion();
			
			int v = 1;
			
			// migrate the versions of the page
			while (v <= cv)
			{
				WikiPage pvi = pageManager.getPageInfo(pageName, v);
				String content = pageManager.getPageText(pageName, v); // here
				
				try {
					Thread.sleep(100); // too many open connections: sleep a bit
				} catch (InterruptedException e) {
					// nothing
				}
				
				System.out.println(String.format("%04d/%d %s", v, cv, pageName));
				
				versioningFileProvider.putPageText(pvi, content);
				
				v++;
			}				
			
		}
		
		// migrate attachments
		AttachmentManager attachmentManager = engine.getAttachmentManager();
		WikiAttachmentProvider attProvider = attachmentManager.getCurrentProvider();
		@SuppressWarnings("unchecked")
		Collection<Attachment> allAttachments = attachmentManager.getAllAttachments();
		
		Iterator<Attachment> attIt = allAttachments.iterator();
		while (attIt.hasNext()) {
			Attachment att = (Attachment) attIt.next();
			
			String attName = att.getName();
			
			int latestVersion = att.getVersion();
			int currentVersion = 1;
			
			while (currentVersion <= latestVersion) {
				Attachment attV = attachmentManager.getAttachmentInfo(
						attName, currentVersion);
				
				InputStream attachmentData = null;
				try  {
					attachmentData = attProvider.getAttachmentData(attV);

					System.out.println(String.format("%04d/%d %s", currentVersion, latestVersion, attName));

					basicAttachmentProvider.putAttachmentData(att, attachmentData);
				} finally {
					close(attachmentData);
				}
				
				try {
					Thread.sleep(100); // too many open connections: sleep a bit
				} catch (InterruptedException e) {
					// nothing
				}

				
				++currentVersion;
			}
		}
	}

	private void close(Closeable cl) {
		if (cl != null) {
			try {
				cl.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}
}
