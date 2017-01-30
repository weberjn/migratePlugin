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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.RuntimeErrorException;

import org.apache.wiki.PageManager;
import org.apache.wiki.WikiContext;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.plugin.InitializablePlugin;
import org.apache.wiki.api.plugin.WikiPlugin;
import org.apache.wiki.event.WikiEngineEvent;
import org.apache.wiki.event.WikiEvent;
import org.apache.wiki.event.WikiEventListener;
import org.apache.wiki.event.WikiEventUtils;
import org.apache.wiki.providers.WikiPageProvider;

import de.jwi.jspwiki.git.GitFileProvider;

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

	public String execute(WikiContext context, Map<String, String> params) throws PluginException
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
		p.put(GitFileProvider.PROP_PAGEDIR, wikiProperties.getProperty("de.jwi.jspwiki.git.migrate.Migrator.pageDir"));
		
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
		GitFileProvider gitFileProvider = new GitFileProvider();
		gitFileProvider.initialize(engine, properties);
		
		PageManager pageManager = engine.getPageManager();
		Collection allPages = pageManager.getAllPages();
		
		Iterator it = allPages.iterator();
		while (it.hasNext())
		{
			WikiPage p = (WikiPage)it.next();
			
			String pageName = p.getName();
			
			WikiPage pv = pageManager.getPageInfo(pageName, WikiPageProvider.LATEST_VERSION);
			
			int cv = pv.getVersion();
			
			int v = 1;
			
			while (v <= cv)
			{
				WikiPage pvi = pageManager.getPageInfo(pageName, v);
				String content = pageManager.getPageText(pageName, v);
				
				System.out.println(String.format("%04d/%d %s", v, cv, pageName));
				
				gitFileProvider.putPageText(pvi, content);
				
				v++;
			}				
		}
	}
}
