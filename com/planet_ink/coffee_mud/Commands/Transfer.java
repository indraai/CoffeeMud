package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.*;

/*
   Copyright 2004-2021 Bo Zimmerman

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
public class Transfer extends At
{
	public Transfer()
	{
	}

	private final String[]	access	= I(new String[] { "TRANSFER" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private String getComResponse(final PrintWriter writer, final BufferedReader reader) throws IOException
	{
		writer.flush();
		final long timeout=System.currentTimeMillis()+(30*1000);
		while((!reader.ready())&&(System.currentTimeMillis()<timeout))
			CMLib.s_sleep(1000);
		if(System.currentTimeMillis()>timeout)
			throw new IOException("Communication failure");
		String s="";
		while(reader.ready())
			s=reader.readLine();
		return s;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		Room room=null;
		if(commands.size()<3)
		{
			mob.tell(L("Transfer whom where? Try all or a mob name or item followed by item name, followerd by a Room ID, target player name, inventory, area name, or room text!"));
			return false;
		}
		final List<String> origCommands = new XVector<String>(commands);
		commands.remove(0);
		String searchName=commands.get(0);
		final Room curRoom=mob.location();
		final Vector<Physical> V=new Vector<Physical>();
		final StringBuffer cmd = new StringBuffer(CMParms.combine(commands,1));
		boolean allFlag=false;
		if(searchName.equalsIgnoreCase("ALL"))
		{
			allFlag=true;
			if(commands.size()>2)
			{
				commands.remove(0);
				searchName=commands.get(0);
			}
			else
				searchName="";
		}
		boolean itemFlag=false;
		if((searchName.equalsIgnoreCase("item")||(searchName.equalsIgnoreCase("items"))))
		{
			itemFlag=true;
			if(commands.size()>2)
			{
				commands.remove(0);
				searchName=commands.get(0);
			}
			else
				searchName="";
		}
		if((searchName.length()==0)&&(allFlag))
		{
			if(itemFlag)
			{
				for(int i=0;i<curRoom.numItems();i++)
					V.add(curRoom.getItem(i));
			}
			else
			for(int i=0;i<curRoom.numInhabitants();i++)
			{
				final MOB M=curRoom.fetchInhabitant(i);
				if(M!=null)
					V.add(M);
			}
		}
		else
		if(itemFlag)
		{
			if(!allFlag)
			{
				final Environmental E=curRoom.fetchFromMOBRoomFavorsItems(mob,null,searchName,Wearable.FILTER_UNWORNONLY);
				if(E instanceof Item)
					V.add((Item)E);
			}
			if((searchName.length()>0)
			&&(!cmd.toString().equals("here"))
			&&(!cmd.toString().equals(".")))
			{
				for(int i=0;i<curRoom.numItems();i++)
				{
					final Item I=curRoom.getItem(i);
					if((I!=null)&&(CMLib.english().containsString(I.name(),searchName)))
						V.add(I);
				}
			}
			if(V.size()==0)
			{
				for(final Enumeration<Room> r=mob.location().getArea().getProperMap();r.hasMoreElements();)
				{
					final Room R=r.nextElement();
					Item I=null;
					int num=1;
					while((num<=1)||(I!=null))
					{
						I=R.findItem(searchName+"."+num);
						if((I!=null)&&(!V.contains(I)))
							V.add(I);
						num++;
						if((!allFlag)&&(V.size()>0))
							break;
					}
					if((!allFlag)&&(V.size()>0))
						break;
				}
			}
			if(V.size()==0)
			{
				try
				{
					for(final Enumeration<Room> r=CMLib.map().rooms();r.hasMoreElements();)
					{
						final Room R=r.nextElement();
						Item I=null;
						int num=1;
						while((num<=1)||(I!=null))
						{
							I=R.findItem(searchName+"."+num);
							if((I!=null)&&(!V.contains(I)))
								V.add(I);
							num++;
							if((!allFlag)&&(V.size()>0))
								break;
						}
						if((!allFlag)&&(V.size()>0))
							break;
					}
				}
				catch (final NoSuchElementException nse)
				{
				}
			}
		}
		else
		{
			if(!allFlag)
			{
				final MOB M=CMLib.sessions().findPlayerOnline(searchName,true);
				if(M!=null)
					V.add(M);
			}
			if(V.size()==0)
			{
				final MOB M=mob.location().fetchInhabitant(searchName);
				if(M!=null)
					V.add(M);
			}
			if(V.size()==0)
			{
				for(final Enumeration<Room> r=mob.location().getArea().getProperMap();r.hasMoreElements();)
				{
					final Room R=r.nextElement();
					MOB M=null;
					int num=1;
					while((num<=1)||(M!=null))
					{
						M=R.fetchInhabitant(searchName+"."+num);
						if((M!=null)&&(!V.contains(M)))
							V.add(M);
						num++;
						if((!allFlag)&&(V.size()>0))
							break;
					}
					if((!allFlag)&&(V.size()>0))
						break;
				}
			}
			if(V.size()==0)
			{
				try
				{
					for(final Enumeration<Room> r=CMLib.map().rooms();r.hasMoreElements();)
					{
						final Room R=r.nextElement();
						MOB M=null;
						int num=1;
						while((num<=1)||(M!=null))
						{
							M=R.fetchInhabitant(searchName+"."+num);
							if((M!=null)&&(!V.contains(M)))
								V.add(M);
							num++;
							if((!allFlag)&&(V.size()>0))
								break;
						}
						if((!allFlag)&&(V.size()>0))
							break;
					}
				}
				catch (final NoSuchElementException nse)
				{
				}
			}
			if((!allFlag)&&(V.size()==0))
			{
				final MOB M=CMLib.players().getLoadPlayer(searchName);
				if(M!=null)
					V.add(M);
			}
		}

		if(V.size()==0)
		{
			try
			{
				final ConvertingEnumeration<SpaceObject,Item> spaceItems=new ConvertingEnumeration<SpaceObject,Item>(
					new FilteredEnumeration<SpaceObject>(CMLib.map().getSpaceObjects(),new Filterer<SpaceObject>()
					{
						@Override
						public boolean passesFilter(final SpaceObject obj)
						{
							return obj instanceof Item;
						}
					}),
					new Converter<SpaceObject,Item>()
					{
						@Override
						public Item convert(final SpaceObject obj)
						{
							return (Item)obj;
						}
					}
				);
				final Environmental E=CMLib.english().fetchEnvironmental(spaceItems, searchName, true);
				if(E instanceof Item)
					V.add((Item)E);
			}
			catch (final NoSuchElementException nse)
			{
			}
		}
		if(V.size()==0)
		{
			try
			{
				final ConvertingEnumeration<SpaceObject,Item> spaceItems=new ConvertingEnumeration<SpaceObject,Item>(
					new FilteredEnumeration<SpaceObject>(CMLib.map().getSpaceObjects(),new Filterer<SpaceObject>()
					{
						@Override
						public boolean passesFilter(final SpaceObject obj)
						{
							return obj instanceof Item;
						}
					}),
					new Converter<SpaceObject,Item>()
					{
						@Override
						public Item convert(final SpaceObject obj)
						{
							return (Item)obj;
						}
					}
				);
				final Environmental E=CMLib.english().fetchEnvironmental(spaceItems, searchName, false);
				if(E instanceof Item)
					V.add((Item)E);
			}
			catch (final NoSuchElementException nse)
			{
			}
		}
		if(V.size()==0)
		{
			try
			{
				final ConvertingEnumeration<BoardableShip,Item> shipItems=new ConvertingEnumeration<BoardableShip,Item>(
					new FilteredEnumeration<BoardableShip>(CMLib.map().ships(),new Filterer<BoardableShip>()
					{
						@Override
						public boolean passesFilter(final BoardableShip obj)
						{
							return obj instanceof Item;
						}
					}),
					new Converter<BoardableShip,Item>()
					{
						@Override
						public Item convert(final BoardableShip obj)
						{
							return (Item)obj;
						}
					}
				);
				final Environmental E=CMLib.english().fetchEnvironmental(shipItems, searchName, false);
				if(E instanceof Item)
					V.add((Item)E);
			}
			catch (final NoSuchElementException nse)
			{
			}
		}
		if(V.size()==0)
		{
			if((!itemFlag)&&(commands.size()>1))
			{
				origCommands.add(1, "ITEM");
				return this.execute(mob, origCommands, metaFlags);
			}
			mob.tell(L("Transfer what?  '@x1' is unknown to you.",searchName));
			return false;
		}

		boolean inventoryFlag=false;
		if(cmd.toString().equalsIgnoreCase("inventory"))
		{
			room=mob.location();
			inventoryFlag=true;
		}
		else
		if(cmd.toString().equalsIgnoreCase("here")||cmd.toString().equalsIgnoreCase("."))
			room=mob.location();
		else
		if(cmd.toString().indexOf('@')>0)
		{
			final String foreignThing=cmd.toString().substring(0,cmd.toString().lastIndexOf('@'));
			String server=cmd.toString().substring(cmd.toString().lastIndexOf('@')+1);
			int port;
			try
			{
				port = CMath.s_int(CMLib.host().executeCommand("GET CM1SERVER PORT"));
			}
			catch (final Exception e)
			{
				Log.errOut(e);
				return false;
			}
			final int ddex=server.indexOf('$');
			final int ddex2=(ddex<0)?-1:server.indexOf('$',ddex+1);
			if((ddex<0)||(ddex2<0))
			{
				mob.tell(L("Server format:  @user$pass$server:port"));
				return false;
			}
			final String user=server.substring(0,ddex);
			final String pass=server.substring(ddex+1,ddex2);
			server=server.substring(ddex2+1);
			if(port<=0)
				port=27733;
			final int pdex=server.lastIndexOf(':');
			if(pdex>0)
			{
				port=CMath.s_int(server.substring(pdex+1));
				server=server.substring(0,pdex);
			}
			final java.net.Socket sock=new java.net.Socket(server,port);
			try
			{
				final PrintWriter writer=new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
				final BufferedReader reader=new BufferedReader(new InputStreamReader(sock.getInputStream()));
				mob.tell(getComResponse(writer,reader));
				writer.write("LOGIN "+user+" "+pass+"\n\r");
				mob.tell(getComResponse(writer,reader));
				writer.write("TARGET "+foreignThing+"\n\r");
				mob.tell(getComResponse(writer,reader));
				writer.write("BLOCK\n\r");
				final String s=getComResponse(writer,reader);
				mob.tell(s);
				String blockEnd;
				if(s.startsWith("[OK ")&&(s.endsWith("]")))
					blockEnd=s.substring(4,s.length()-1);
				else
				{
					mob.tell(L("Communication failure."));
					return false;
				}
				for(int i=0;i<V.size();i++)
				{
					if(V.get(i) instanceof Item)
					{
						final Item I=(Item)V.get(i);
						final Room itemRoom=CMLib.map().roomLocation(I);
						if((itemRoom!=null)
						&&(CMSecurity.isAllowed(mob, itemRoom, CMSecurity.SecFlag.TRANSFER))
						&&(CMSecurity.isAllowed(mob, room, CMSecurity.SecFlag.TRANSFER)))
						{
							final StringBuffer itemXML=CMLib.coffeeMaker().getItemXML(I);
							writer.write("IMPORT <ITEMS>"+itemXML+"</ITEMS>"+blockEnd);
							mob.tell(getComResponse(writer,reader));
						}
					}
					else
					if(V.get(i) instanceof MOB)
					{
						final MOB M=(MOB)V.get(i);
						final Room mobRoom=CMLib.map().roomLocation(M);
						if((mobRoom!=null)
						&&(CMSecurity.isAllowed(mob, mobRoom, CMSecurity.SecFlag.TRANSFER))
						&&(CMSecurity.isAllowed(mob, room, CMSecurity.SecFlag.TRANSFER)))
						{
							final StringBuffer mobXML=CMLib.coffeeMaker().getMobXML(M);
							writer.write("IMPORT <MOBS>"+mobXML+"<MOBS>"+blockEnd);
							mob.tell(getComResponse(writer,reader));
						}
					}
				}
				if(mob.playerStats().getTranPoofOut().length()==0)
					mob.tell(L("Done."));
				writer.write("QUIT "+blockEnd);
				CMLib.s_sleep(500);
				return true;
			}
			catch(final IOException e)
			{
				mob.tell(e.getMessage());
			}
			finally
			{
				sock.close();
			}
		}
		else
		if(CMLib.map().getRoom(cmd.toString())!=null)
			room=CMLib.map().getRoom(cmd.toString());
		else
		if(CMLib.directions().getDirectionCode(cmd.toString())>=0)
			room=mob.location().getRoomInDir(CMLib.directions().getDirectionCode(cmd.toString()));
		else
			room=CMLib.map().findWorldRoomLiberally(mob,cmd.toString(),"RIPME",100,120000);

		if(room==null)
		{
			mob.tell(L("Transfer where? '@x1' is unknown.  Enter a Room ID, player name, area name, or room text!",cmd.toString()));
			return false;
		}
		for(int i=0;i<V.size();i++)
		{
			if(V.get(i) instanceof Item)
			{
				final Item I=(Item)V.get(i);
				final Room itemRoom=CMLib.map().roomLocation(I);
				if((itemRoom!=null)
				&&((!room.isContent(I))||(inventoryFlag))
				&&(CMSecurity.isAllowed(mob, itemRoom, CMSecurity.SecFlag.TRANSFER))
				&&(CMSecurity.isAllowed(mob, room, CMSecurity.SecFlag.TRANSFER)))
				{
					if(inventoryFlag)
						mob.moveItemTo(I,ItemPossessor.Expire.Never,ItemPossessor.Move.Followers);
					else
					{
						room.moveItemTo(I,ItemPossessor.Expire.Never,ItemPossessor.Move.Followers);
						if(I instanceof SpaceObject)
							((SpaceObject)I).setSpeed(0);
						if(I instanceof SpaceShip)
							((SpaceShip)I).dockHere(room);
					}
				}
			}
			else
			if(V.get(i) instanceof MOB)
			{
				final MOB M=(MOB)V.get(i);
				final Room mobRoom=CMLib.map().roomLocation(M);
				if((mobRoom!=null)
				&&(!room.isInhabitant(M))
				&&(CMSecurity.isAllowed(mob, mobRoom, CMSecurity.SecFlag.TRANSFER))
				&&(CMSecurity.isAllowed(mob, room, CMSecurity.SecFlag.TRANSFER)))
				{
					if(M.isPlayer() && (!CMLib.flags().isInTheGame(M, true)))
						M.setLocation(room);
					else
					{
						if((mob.playerStats().getTranPoofOut().length()>0)&&(mob.location()!=null))
							M.location().show(M,M.location(),CMMsg.MSG_LEAVE|CMMsg.MASK_ALWAYS,mob.playerStats().getTranPoofOut());
						room.bringMobHere(M,true);
					}
					if(mob.playerStats().getTranPoofIn().length()>0)
						M.location().show(M,M.location(),CMMsg.MSG_ENTER|CMMsg.MASK_ALWAYS,mob.playerStats().getTranPoofIn());
					if(!M.isMonster() && (room.isInhabitant(M)))
						CMLib.commands().postLook(M,true);
				}
			}
		}
		if(mob.playerStats().getTranPoofOut().length()==0)
			mob.tell(L("Done."));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowed(mob, mob.location(), CMSecurity.SecFlag.TRANSFER);
	}

}
