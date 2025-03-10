package com.planet_ink.coffee_mud.Libraries;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMSecurity.DbgFlag;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.collections.MultiEnumeration.MultiEnumeratorBuilder;
import com.planet_ink.coffee_mud.core.interfaces.BoundedObject;
import com.planet_ink.coffee_mud.core.interfaces.BoundedObject.BoundedCube;
import com.planet_ink.coffee_mud.core.interfaces.LandTitle;
import com.planet_ink.coffee_mud.core.interfaces.MsgListener;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;
import com.planet_ink.coffee_mud.core.interfaces.PrivateProperty;
import com.planet_ink.coffee_mud.core.interfaces.SpaceObject;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.ShipDirComponent.ShipDir;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.Map.Entry;
/*
   Copyright 2001-2021 Bo Zimmerman

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
public class CMMap extends StdLibrary implements WorldMap
{
	@Override
	public String ID()
	{
		return "CMMap";
	}

	protected static final double		ZERO_ALMOST				= 0.000001;
	protected static final BigDecimal	ZERO					= BigDecimal.valueOf(0.0);
	protected static final BigDecimal	ALMOST_ZERO				= BigDecimal.valueOf(ZERO_ALMOST);
	protected static final BigDecimal	ONE						= BigDecimal.valueOf(1L);
	protected static final BigDecimal	TWO						= BigDecimal.valueOf(2L);
	protected static final BigDecimal	TEN						= BigDecimal.valueOf(10L);
	protected static final BigDecimal	ONE_THOUSAND			= BigDecimal.valueOf(1000);
	protected static final double		PI_ALMOST				= Math.PI - ZERO_ALMOST;
	protected static final double		PI_TIMES_2_ALMOST		= Math.PI * 2.0 - ZERO_ALMOST;
	protected static final double		PI_TIMES_2				= Math.PI * 2.0;
	protected static final double		PI_BY_2					= Math.PI / 2.0;
	protected static final double		PI_TIMES_1ANDAHALF		= Math.PI * 1.5;
	protected final int					QUADRANT_WIDTH			= 10;
	protected static MOB				deityStandIn			= null;
	protected long						lastVReset				= 0;
	protected CMNSortSVec<Area>			areasList				= new CMNSortSVec<Area>();
	protected CMNSortSVec<Deity>		deitiesList				= new CMNSortSVec<Deity>();
	protected List<BoardableShip>		shipList				= new SVector<BoardableShip>();
	protected List<PostOffice>			postOfficeList			= new SVector<PostOffice>();
	protected List<Auctioneer>			auctionHouseList		= new SVector<Auctioneer>();
	protected List<Banker>				bankList				= new SVector<Banker>();
	protected List<Librarian>			libraryList				= new SVector<Librarian>();
	protected Map<String, Set<Places>>	holyPlaces				= new SHashtable<String, Set<Places>>();
	protected RTree<SpaceObject>		space					= new RTree<SpaceObject>();
	protected Map<String, Object>		SCRIPT_HOST_SEMAPHORES	= new Hashtable<String, Object>();

	protected static final Comparator<Area>	areaComparator = new Comparator<Area>()
	{
		@Override
		public int compare(final Area o1, final Area o2)
		{
			if(o1==null)
				return (o2==null)?0:-1;
			return o1.Name().compareToIgnoreCase(o2.Name());
		}
	};

	public Map<Integer,List<WeakReference<MsgListener>>>
								globalHandlers   		= new SHashtable<Integer,List<WeakReference<MsgListener>>>();
	public Map<String,SLinkedList<LocatedPair>>
								scriptHostMap			= new STreeMap<String,SLinkedList<LocatedPair>>();

	private static final long EXPIRE_1MIN	= 1*60*1000;
	private static final long EXPIRE_5MINS	= 5*60*1000;
	private static final long EXPIRE_10MINS	= 10*60*1000;
	private static final long EXPIRE_20MINS	= 20*60*1000;
	private static final long EXPIRE_30MINS	= 30*60*1000;
	private static final long EXPIRE_1HOUR	= 60*60*1000;

	private static class LocatedPairImpl implements LocatedPair
	{
		final WeakReference<Room> roomW;
		final WeakReference<PhysicalAgent> objW;

		private LocatedPairImpl(final Room room, final PhysicalAgent host)
		{
			roomW = new WeakReference<Room>(room);
			objW = new WeakReference<PhysicalAgent>(host);
		}

		@Override
		public Room room()
		{
			return roomW.get();
		}

		@Override
		public PhysicalAgent obj()
		{
			return objW.get();
		}
	}

	private static Filterer<Area> planetsAreaFilter=new Filterer<Area>()
	{
		@Override
		public boolean passesFilter(final Area obj)
		{
			return (obj instanceof SpaceObject) && (!(obj instanceof SpaceShip));
		}
	};

	private static Filterer<Area> mundaneAreaFilter=new Filterer<Area>()
	{
		@Override
		public boolean passesFilter(final Area obj)
		{
			return !(obj instanceof SpaceObject);
		}
	};

	private static Filterer<Area> topLevelAreaFilter=new Filterer<Area>()
	{
		@Override
		public boolean passesFilter(final Area obj)
		{
			return ! obj.getParents().hasMoreElements();
		}
	};

	protected int getGlobalIndex(final List<Environmental> list, final String name)
	{
		if(list.size()==0)
			return -1;
		int start=0;
		int end=list.size()-1;
		while(start<=end)
		{
			final int mid=(end+start)/2;
			final int comp=list.get(mid).Name().compareToIgnoreCase(name);
			if(comp==0)
				return mid;
			else
			if(comp>0)
				end=mid-1;
			else
				start=mid+1;

		}
		return -1;
	}

	@Override
	public void renamedArea(final Area theA)
	{
		areasList.reSort(theA);
	}

	// areas
	@Override
	public int numAreas()
	{
		return areasList.size();
	}

	@Override
	public void addArea(final Area newOne)
	{
		areasList.add(newOne);
		if((newOne instanceof SpaceObject)&&(!space.contains((SpaceObject)newOne)))
			space.insert((SpaceObject)newOne);
	}

	@Override
	public void delArea(final Area oneToDel)
	{
		areasList.remove(oneToDel);
		if((oneToDel instanceof SpaceObject)&&(space.contains((SpaceObject)oneToDel)))
			space.remove((SpaceObject)oneToDel);
		Resources.removeResource("SYSTEM_AREA_FINDER_CACHE");
	}

	@Override
	public Area getModelArea(final Area A)
	{
		if(A!=null)
		{
			if(CMath.bset(A.flags(),Area.FLAG_INSTANCE_CHILD))
			{
				final int x=A.Name().indexOf('_');
				if((x>0)
				&&(CMath.isInteger(A.Name().substring(0,x))))
				{
					final Area A2=getArea(A.Name().substring(x+1));
					if(A2!=null)
						return A2;
				}
			}
		}
		return A;
	}

	@Override
	public Area getArea(final String calledThis)
	{
		final Map<String,Area> finder=getAreaFinder();
		Area A=finder.get(calledThis.toLowerCase());
		if((A!=null)&&(!A.amDestroyed()))
			return A;
		A=areasList.find(calledThis);
		if((A!=null)&&(!A.amDestroyed()))
		{
			if(!CMProps.getBoolVar(CMProps.Bool.MAPFINDSNOCACHE))
				finder.put(calledThis.toLowerCase(), A);
			return A;
		}
		return null;
	}

	@Override
	public Area findAreaStartsWith(final String calledThis)
	{
		final boolean disableCaching=CMProps.getBoolVar(CMProps.Bool.MAPFINDSNOCACHE);
		Area A=getArea(calledThis);
		if(A!=null)
			return A;
		final Map<String,Area> finder=getAreaFinder();
		A=finder.get(calledThis.toLowerCase());
		if((A!=null)&&(!A.amDestroyed()))
			return A;
		for(final Enumeration<Area> a=areas();a.hasMoreElements();)
		{
			A=a.nextElement();
			if(A.Name().toUpperCase().startsWith(calledThis))
			{
				if(!disableCaching)
					finder.put(calledThis.toLowerCase(), A);
				return A;
			}
		}
		return null;
	}

	@Override
	public Area findArea(final String calledThis)
	{
		final boolean disableCaching=CMProps.getBoolVar(CMProps.Bool.MAPFINDSNOCACHE);
		Area A=findAreaStartsWith(calledThis);
		if(A!=null)
			return A;
		final Map<String,Area> finder=getAreaFinder();
		A=finder.get(calledThis.toLowerCase());
		if((A!=null)&&(!A.amDestroyed()))
			return A;
		for(final Enumeration<Area> a=areas();a.hasMoreElements();)
		{
			A=a.nextElement();
			if(CMLib.english().containsString(A.Name(),calledThis))
			{
				if(!disableCaching)
					finder.put(calledThis.toLowerCase(), A);
				return A;
			}
		}
		return null;
	}

	@Override
	public Enumeration<Area> areas()
	{
		return new IteratorEnumeration<Area>(areasList.iterator());
	}

	@Override
	public Enumeration<Area> areasPlusShips()
	{
		final MultiEnumeration<Area> m=new MultiEnumeration<Area>(new IteratorEnumeration<Area>(areasList.iterator()));
		m.addEnumeration(shipAreaEnumerator(null));
		return m;
	}

	@Override
	public Enumeration<Area> mundaneAreas()
	{
		return new FilteredEnumeration<Area>(areas(),mundaneAreaFilter);
	}

	@Override
	public Enumeration<Area> spaceAreas()
	{
		return new FilteredEnumeration<Area>(areas(),planetsAreaFilter);
	}

	@Override
	public Enumeration<Area> topAreas()
	{
		return new FilteredEnumeration<Area>(areas(),topLevelAreaFilter);
	}

	@Override
	public Enumeration<String> roomIDs()
	{
		return new Enumeration<String>()
		{
			private volatile Enumeration<String> roomIDEnumerator=null;
			private volatile Enumeration<Area> areaEnumerator=areasPlusShips();

			@Override
			public boolean hasMoreElements()
			{
				boolean hasMore = (roomIDEnumerator != null) && roomIDEnumerator.hasMoreElements();
				while(!hasMore)
				{
					if((areaEnumerator == null)||(!areaEnumerator.hasMoreElements()))
					{
						roomIDEnumerator=null;
						areaEnumerator = null;
						return false;
					}
					final Area A=areaEnumerator.nextElement();
					roomIDEnumerator=A.getProperRoomnumbers().getRoomIDs();
					hasMore = (roomIDEnumerator != null) && roomIDEnumerator.hasMoreElements();
				}
				return hasMore;
			}

			@Override
			public String nextElement()
			{
				return hasMoreElements() ? (String) roomIDEnumerator.nextElement() : null;
			}
		};
	}

	@Override
	public Area getFirstArea()
	{
		if (areas().hasMoreElements())
			return areas().nextElement();
		return null;
	}

	@Override
	public Area getDefaultParentArea()
	{
		final String defaultParentAreaName=CMProps.getVar(CMProps.Str.DEFAULTPARENTAREA);
		if((defaultParentAreaName!=null)&&(defaultParentAreaName.trim().length()>0))
			return getArea(defaultParentAreaName.trim());
		return null;
	}

	@Override
	public Area getRandomArea()
	{
		Area A=null;
		while((numAreas()>0)&&(A==null))
		{
			try
			{
				A=areasList.get(CMLib.dice().roll(1,numAreas(),-1));
			}
			catch(final ArrayIndexOutOfBoundsException e)
			{
			}
		}
		return A;
	}

	@Override
	public void addGlobalHandler(final MsgListener E, final int category)
	{
		if(E==null)
			return;
		List<WeakReference<MsgListener>> V=globalHandlers.get(Integer.valueOf(category));
		if(V==null)
		{
			V=new SLinkedList<WeakReference<MsgListener>>();
			globalHandlers.put(Integer.valueOf(category),V);
		}
		synchronized(V)
		{
			for (final WeakReference<MsgListener> W : V)
			{
				if(W.get()==E)
					return;
			}
			V.add(new WeakReference<MsgListener>(E));
		}
	}

	@Override
	public void delGlobalHandler(final MsgListener E, final int category)
	{
		final List<WeakReference<MsgListener>> V=globalHandlers.get(Integer.valueOf(category));
		if((E==null)||(V==null))
			return;
		synchronized(V)
		{
			for (final WeakReference<MsgListener> W : V)
			{
				if(W.get()==E)
					V.remove(W);
			}
		}
	}

	@Override
	public MOB deity()
	{
		if(deities().hasMoreElements())
			return deities().nextElement();
		if((deityStandIn==null)
		||(deityStandIn.amDestroyed())
		||(deityStandIn.amDead())
		||(deityStandIn.location()==null)
		||(deityStandIn.location().isInhabitant(deityStandIn)))
		{
			if(deityStandIn!=null)
				deityStandIn.destroy();
			final MOB everywhereMOB=CMClass.getMOB("StdMOB");
			everywhereMOB.setName(L("god"));
			everywhereMOB.setLocation(this.getRandomRoom());
			deityStandIn=everywhereMOB;
		}
		return deityStandIn;
	}

	@Override
	public MOB getFactoryMOBInAnyRoom()
	{
		return getFactoryMOB(this.getRandomRoom());
	}

	@Override
	public MOB getFactoryMOB(final Room R)
	{
		final MOB everywhereMOB=CMClass.getFactoryMOB();
		everywhereMOB.setName(L("somebody"));
		everywhereMOB.setLocation(R);
		return everywhereMOB;
	}

	@Override
	public boolean isObjectInSpace(final SpaceObject O)
	{
		synchronized(space)
		{
			return space.contains(O);
		}
	}

	@Override
	public void delObjectInSpace(final SpaceObject O)
	{
		synchronized(space)
		{
			space.remove(O);
		}
	}

	@Override
	public void addObjectToSpace(final SpaceObject O, final long[] coords)
	{
		synchronized(space)
		{
			O.coordinates()[0]=coords[0];
			O.coordinates()[1]=coords[1];
			O.coordinates()[2]=coords[2];
			space.insert(O); // won't accept dups, so is ok
		}
	}

	@Override
	public long getDistanceFrom(final long[] coord1, final long[] coord2)
	{
		final BigInteger coord_0 = BigInteger.valueOf(coord1[0]).subtract(BigInteger.valueOf(coord2[0]));
		final BigInteger coord_0m = coord_0.multiply(coord_0);
		final BigInteger coord_1 = BigInteger.valueOf(coord1[1]).subtract(BigInteger.valueOf(coord2[1]));
		final BigInteger coord_1m = coord_1.multiply(coord_1);
		final BigInteger coord_2 = BigInteger.valueOf(coord1[2]).subtract(BigInteger.valueOf(coord2[2]));
		final BigInteger coord_2m = coord_2.multiply(coord_2);
		final BigInteger coords_all = coord_0m.add(coord_1m).add(coord_2m);
		return Math.round(Math.sqrt(coords_all.doubleValue()));
	}

	@Override
	public long getDistanceFrom(final SpaceObject O1, final SpaceObject O2)
	{
		return getDistanceFrom(O1.coordinates(),O2.coordinates());
	}

	protected BigDecimal getBigDistanceFrom(final long[] coord1, final long[] coord2)
	{
		final BigDecimal coord_0 = BigDecimal.valueOf(coord1[0]).subtract(BigDecimal.valueOf(coord2[0]));
		final BigDecimal coord_0m = coord_0.multiply(coord_0);
		final BigDecimal coord_1 = BigDecimal.valueOf(coord1[1]).subtract(BigDecimal.valueOf(coord2[1]));
		final BigDecimal coord_1m = coord_1.multiply(coord_1);
		final BigDecimal coord_2 = BigDecimal.valueOf(coord1[2]).subtract(BigDecimal.valueOf(coord2[2]));
		final BigDecimal coord_2m = coord_2.multiply(coord_2);
		final BigDecimal coords_all = coord_0m.add(coord_1m).add(coord_2m);
		final BigDecimal val = bigSqrt(coords_all);
		return val;
	}

	@Override
	public String getSectorName(final long[] coordinates)
	{
		final String[] xsecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_X_NAMES);
		final String[] ysecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_Y_NAMES);
		final String[] zsecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_Z_NAMES);

		final long dmsPerXSector = SpaceObject.Distance.GalaxyRadius.dm / xsecs.length;
		final long dmsPerYSector = SpaceObject.Distance.GalaxyRadius.dm / ysecs.length;
		final long dmsPerZSector = SpaceObject.Distance.GalaxyRadius.dm / zsecs.length;

		final int secDeX = (int)((coordinates[0] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerXSector / 2);
		final int secDeY = (int)((coordinates[1] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerYSector / 2);
		final int secDeZ = (int)((coordinates[2] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerZSector / 2);

		final StringBuilder sectorName = new StringBuilder("");
		sectorName.append(xsecs[(secDeX < 0)?(xsecs.length+secDeX):secDeX]).append(" ");
		sectorName.append(ysecs[(secDeY < 0)?(ysecs.length+secDeY):secDeY]).append(" ");
		sectorName.append(zsecs[(secDeZ < 0)?(zsecs.length+secDeZ):secDeZ]);
		return sectorName.toString();
	}

	@Override
	public long[] getInSectorCoords(final long[] coordinates)
	{
		final String[] xsecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_X_NAMES);
		final String[] ysecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_Y_NAMES);
		final String[] zsecs=CMProps.getListFileStringList(CMProps.ListFile.TECH_SECTOR_Z_NAMES);

		final long dmsPerXSector = SpaceObject.Distance.GalaxyRadius.dm / xsecs.length;
		final long dmsPerYSector = SpaceObject.Distance.GalaxyRadius.dm / ysecs.length;
		final long dmsPerZSector = SpaceObject.Distance.GalaxyRadius.dm / zsecs.length;

		final int secDeX = (int)((coordinates[0] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerXSector / (2 * (coordinates[0]<0?-1:1)));
		final int secDeY = (int)((coordinates[1] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerYSector / (2 * (coordinates[0]<0?-1:1)));
		final int secDeZ = (int)((coordinates[2] % SpaceObject.Distance.GalaxyRadius.dm) / dmsPerZSector / (2 * (coordinates[0]<0?-1:1)));

		final long[] sectorCoords = Arrays.copyOf(coordinates, 3);
		for(int i=0;i<sectorCoords.length;i++)
		{
			if(sectorCoords[i]<0)
				sectorCoords[i]*=-1;
		}
		sectorCoords[0] -= (secDeX * dmsPerXSector) / 1000;
		sectorCoords[1] -= (secDeY * dmsPerYSector) / 1000;
		sectorCoords[2] -= (secDeZ * dmsPerZSector) / 1000;
		return sectorCoords;
	}

	@Override
	public void moveSpaceObject(final SpaceObject O, final double[] accelDirection, final double newAcceleration)
	{
		final double newSpeed = moveSpaceObject(O.direction(),O.speed(),accelDirection,newAcceleration);
		O.setSpeed(newSpeed);
	}

	@Override
	public double getAngleDelta(final double[] fromAngle, final double[] toAngle)
	{
		final double x1=Math.sin(fromAngle[1])*Math.cos(fromAngle[0]);
		final double y1=Math.sin(fromAngle[1])*Math.sin(fromAngle[0]);
		final double z1=Math.cos(fromAngle[1]);
		final double x2=Math.sin(toAngle[1])*Math.cos(toAngle[0]);
		final double y2=Math.sin(toAngle[1])*Math.sin(toAngle[0]);
		final double z2=Math.cos(toAngle[1]);
		double pitchDOTyaw=x1*x2+y1*y2+z1*z2;
		if(pitchDOTyaw>1)
			pitchDOTyaw=(2-pitchDOTyaw);
		if(pitchDOTyaw<-1)
			pitchDOTyaw=(-1*pitchDOTyaw)-2;
		final double finalDelta=Math.acos(pitchDOTyaw);
		if(Double.isNaN(finalDelta) || Double.isInfinite(finalDelta))
		{
			Log.errOut("finalDelta = "+ finalDelta+"= ("+fromAngle[0]+","+fromAngle[1]+") -> ("+toAngle[0]+","+toAngle[1]+")");
			Log.errOut("pitchDOTyaw = " + pitchDOTyaw+", x1 = "+ x1 + ", y1 = "+ y1 + ", z1 = "+ z1 + ", x2 = "+ x2 + ", y2 = "+ y2);
		}
		return finalDelta;
	}

	@Override
	public double[] getFacingAngleDiff(final double[] fromAngle, final double[] toAngle)
	{
		final double fromYaw = fromAngle[0];
		final double fromPitch = (fromAngle[1] > Math.PI) ? Math.abs(Math.PI-fromAngle[1]) : fromAngle[1];

		final double toYaw = toAngle[0];
		final double toPitch = (toAngle[1] > Math.PI) ? Math.abs(Math.PI-toAngle[1]) : toAngle[1];

		final double[] delta = new double[2];
		if(toYaw != fromYaw)
		{
			if(toYaw > fromYaw)
			{
				delta[0]=(toYaw-fromYaw);
				if(delta[0] > Math.PI)
					delta[0] = -((PI_TIMES_2-toYaw)+fromYaw);
			}
			else
			{
				delta[0]=(toYaw-fromYaw);
				if(delta[0] < -Math.PI)
					delta[0] = -((PI_TIMES_2-fromYaw)+toYaw);
			}
		}
		delta[1]=(toPitch-fromPitch);
		return delta;
	}

	@Override
	public double moveSpaceObject(final double[] curDirection, final double curSpeed, final double[] accelDirection, final double newAcceleration)
	{
		if(newAcceleration <= 0.0)
			return curSpeed;

		final double curDirectionYaw = curDirection[0];
		final double curDirectionPitch = (curDirection[1] > Math.PI) ? Math.abs(Math.PI-curDirection[1]) : curDirection[1];

		final double accelDirectionYaw = accelDirection[0];
		final double accelDirectionPitch = (accelDirection[1] > Math.PI) ? Math.abs(Math.PI-accelDirection[1]) : accelDirection[1];

		final double currentSpeed = curSpeed;
		final double acceleration = newAcceleration;

		double yawDelta = (curDirectionYaw >  accelDirectionYaw) ? (curDirectionYaw - accelDirectionYaw) : (accelDirectionYaw - curDirectionYaw);
		// 350 and 10, diff = 340 + -360 = 20
		if(yawDelta > Math.PI)
			yawDelta=PI_TIMES_2-yawDelta;

		double pitchDelta = (curDirectionPitch >  accelDirectionPitch) ? (curDirectionPitch - accelDirectionPitch) : (accelDirectionPitch - curDirectionPitch);
		// 170 and 10 = 160, which is correct!
		if(pitchDelta > Math.PI)
			pitchDelta=Math.PI-pitchDelta;
		if(Math.abs(pitchDelta-Math.PI)<ZERO_ALMOST)
			yawDelta=0.0;

		final double anglesDelta =  getAngleDelta(curDirection, accelDirection);
		final double accelerationMultiplier = acceleration / currentSpeed;
		double newDirectionYaw;
		if(yawDelta < 0.1)
			newDirectionYaw = accelDirectionYaw;
		else
		{
			newDirectionYaw = curDirectionYaw + ((curDirectionYaw > accelDirectionYaw) ? -(accelerationMultiplier * Math.sin(yawDelta)) : (accelerationMultiplier * Math.sin(yawDelta)));
			if((newDirectionYaw > 0.0) && ((PI_TIMES_2 - newDirectionYaw) < ZERO_ALMOST))
				newDirectionYaw=0.0;
		}
		if (newDirectionYaw < 0.0)
			newDirectionYaw = PI_TIMES_2 + newDirectionYaw;
		double newDirectionPitch;
		if(pitchDelta < 0.1)
			newDirectionPitch = accelDirectionPitch;
		else
			newDirectionPitch = curDirectionPitch + ((curDirectionPitch > accelDirectionPitch) ? -(accelerationMultiplier * Math.sin(pitchDelta)) : (accelerationMultiplier * Math.sin(pitchDelta)));
		if (newDirectionPitch < 0.0)
			newDirectionPitch = PI_TIMES_2 + newDirectionPitch;

		double newSpeed = currentSpeed + (acceleration * Math.cos(anglesDelta));
		if(newSpeed < 0)
		{
			newSpeed = -newSpeed;
			newDirectionYaw = accelDirectionYaw;
			newDirectionPitch = accelDirectionPitch;
		}
		curDirection[0]=newDirectionYaw;
		curDirection[1]=newDirectionPitch;
		if(Double.isInfinite(newSpeed) || Double.isNaN(newSpeed))
		{
			Log.errOut("Invalid new speed: "+newSpeed + "("+currentSpeed+"+"+"("+acceleration+"*Math.cos("+anglesDelta+"))");
			return curSpeed;
		}
		return newSpeed;
	}

	@Override
	public double[] getOppositeDir(final double[] dir)
	{
		if((dir[1]<ZERO_ALMOST)||(dir[1]>PI_ALMOST))
			return new double[]{dir[0], Math.PI-dir[1]};
		final double[] newDir = new double[]{Math.PI+dir[0],Math.PI-dir[1]};
		if(newDir[0] >= PI_TIMES_2)
			newDir[0] -= PI_TIMES_2;
		return newDir;
	}

	@Override
	public ShipDirComponent.ShipDir getDirectionFromDir(final double[] facing, final double roll, final double[] direction)
	{
		//Log.debugOut("facing="+(Math.toDegrees(facing[0]) % 360.0)+","+(Math.toDegrees(facing[1]) % 180.0));
		//Log.debugOut("direction="+(Math.toDegrees(direction[0]) % 360.0)+","+(Math.toDegrees(direction[1]) % 180.0));
		double yD = ((Math.toDegrees(facing[0]) % 360.0) - (Math.toDegrees(direction[0]) % 360.0)) % 360.0;
		if(yD < 0)
			yD = 360.0 + yD;
		final double pD = Math.abs(((Math.toDegrees(facing[1]) % 180.0) - (Math.toDegrees(direction[1]) % 180.0)) % 180.0);
		//Log.debugOut("yD,pD="+yD+","+pD);
		double rD = (yD + (Math.toDegrees(roll) % 360.0)) % 360.0;
		if(rD < 0)
			rD = 360.0 + rD;
		//Log.debugOut("rD="+rD);
		if(pD<45 || pD > 135)
		{
			if(yD < 45.0 || yD > 315.0)
				return ShipDir.FORWARD;
			if(yD> 135.0 && yD < 225.0)
				return ShipDir.AFT;
		}
		if(rD >= 315.0 || rD<45.0)
			return ShipDir.DORSEL;
		if(rD >= 45.0 && rD <135.0)
			return ShipDir.PORT;
		if(rD >= 135.0 && rD <225.0)
			return ShipDir.VENTRAL;
		if(rD >= 225.0 && rD <315.0)
			return ShipDir.STARBOARD;
		return ShipDir.AFT;
	}

	@Override
	public double[] getDirection(final SpaceObject fromObj, final SpaceObject toObj)
	{
		return getDirection(fromObj.coordinates(),toObj.coordinates());
	}

	protected void moveSpaceObject(final SpaceObject O, final long x, final long y, final long z)
	{
		synchronized(space)
		{
			final boolean reAdd=space.contains(O);
			if(reAdd)
				space.remove(O);
			O.coordinates()[0]=x;
			O.coordinates()[1]=y;
			O.coordinates()[2]=z;
			if(reAdd)
				space.insert(O);
		}
	}

	@Override
	public void moveSpaceObject(final SpaceObject O, final long[] coords)
	{
		moveSpaceObject(O, coords[0], coords[1], coords[2]);
	}

	@Override
	public void moveSpaceObject(final SpaceObject O)
	{
		if(O.speed()>0)
		{
			final double x1=Math.cos(O.direction()[0])*Math.sin(O.direction()[1]);
			final double y1=Math.sin(O.direction()[0])*Math.sin(O.direction()[1]);
			final double z1=Math.cos(O.direction()[1]);
			moveSpaceObject(O,O.coordinates()[0]+Math.round(CMath.mul(O.speed(),x1)),
							O.coordinates()[1]+Math.round(CMath.mul(O.speed(),y1)),
							O.coordinates()[2]+Math.round(CMath.mul(O.speed(),z1)));
		}
	}

	@Override
	public double[] getDirection(final long[] fromCoords, final long[] toCoords)
	{
		final double[] dir=new double[2];
		final double x=toCoords[0]-fromCoords[0];
		final double y=toCoords[1]-fromCoords[1];
		final double z=toCoords[2]-fromCoords[2];
		if((x!=0)||(y!=0))
		{
			if(x<0)
				dir[0]=Math.PI-Math.asin(y/Math.sqrt((x*x)+(y*y)));
			else
				dir[0]=Math.asin(y/Math.sqrt((x*x)+(y*y)));
			if(dir[0] > 2*Math.PI)
				dir[0] = Math.abs(2*Math.PI-dir[0]);
		}
		if((x!=0)||(y!=0)||(z!=0))
			dir[1]=Math.acos(z/Math.sqrt((z*z)+(y*y)+(x*x)));
		if(dir[1] > Math.PI)
			dir[1] = Math.abs(Math.PI-dir[1]);
		return dir;
	}

	@Override
	public long[] moveSpaceObject(final long[] coordinates, final double[] direction, final long speed)
	{
		if(speed>0)
		{
			final double x1=Math.cos(direction[0])*Math.sin(direction[1]);
			final double y1=Math.sin(direction[0])*Math.sin(direction[1]);
			final double z1=Math.cos(direction[1]);
			return new long[]{coordinates[0]+Math.round(CMath.mul(speed,x1)),
							coordinates[1]+Math.round(CMath.mul(speed,y1)),
							coordinates[2]+Math.round(CMath.mul(speed,z1))};
		}
		return coordinates;
	}

	@Override
	public long[] getLocation(final long[] oldLocation, final double[] direction, final long distance)
	{
		final double x1=Math.cos(direction[0])*Math.sin(direction[1]);
		final double y1=Math.sin(direction[0])*Math.sin(direction[1]);
		final double z1=Math.cos(direction[1]);
		final long[] location=new long[3];
		location[0]=oldLocation[0]+Math.round(CMath.mul(distance,x1));
		location[1]=oldLocation[1]+Math.round(CMath.mul(distance,y1));
		location[2]=oldLocation[2]+Math.round(CMath.mul(distance,z1));
		return location;
	}

	@Override
	public long getRelativeSpeed(final SpaceObject O1, final SpaceObject O2)
	{
		return Math.round(Math.sqrt(( CMath.bigMultiply(O1.speed(),O1.coordinates()[0])
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[0]).multiply(CMath.bigMultiply(O1.speed(),O1.coordinates()[0])))
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[0])))
									.add(CMath.bigMultiply(O1.speed(),O1.coordinates()[1])
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[1]).multiply(CMath.bigMultiply(O1.speed(),O1.coordinates()[1])))
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[1])))
									.add(CMath.bigMultiply(O1.speed(),O1.coordinates()[2])
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[2]).multiply(CMath.bigMultiply(O1.speed(),O1.coordinates()[2])))
										.subtract(CMath.bigMultiply(O2.speed(),O2.coordinates()[2]))).doubleValue()));
	}

	@Override
	public SpaceObject findSpaceObject(final String s, final boolean exactOnly)
	{
		final Iterable<SpaceObject> i=new Iterable<SpaceObject>()
		{
			@Override
			public Iterator<SpaceObject> iterator()
			{
				return new EnumerationIterator<SpaceObject>(space.objects());
			}

		};
		return (SpaceObject)CMLib.english().fetchEnvironmental(i, s, exactOnly);
	}

	@Override
	public SpaceObject getSpaceObject(final CMObject o, final boolean ignoreMobs)
	{
		if(o instanceof SpaceObject)
		{
			if(o instanceof BoardableShip)
			{
				final Item I=((BoardableShip)o).getShipItem();
				if(I instanceof SpaceObject)
					return (SpaceObject)I;
			}
			return (SpaceObject)o;
		}
		if(o instanceof Item)
		{
			if(((Item)o).container()!=null)
				return getSpaceObject(((Item)o).container(),ignoreMobs);
			else
				return getSpaceObject(((Item)o).owner(),ignoreMobs);
		}
		if(o instanceof MOB)
			return ignoreMobs?null:getSpaceObject(((MOB)o).location(),false);
		if(o instanceof Room)
			return getSpaceObject(((Room)o).getArea(),ignoreMobs);
		if(o instanceof Area)
		{
			for(final Enumeration<Area> a=((Area)o).getParents();a.hasMoreElements();)
			{
				final SpaceObject obj=getSpaceObject(a.nextElement(),ignoreMobs);
				if(obj != null)
					return obj;
			}
		}
		return null;
	}

	@Override
	public Enumeration<SpaceObject> getSpaceObjects()
	{
		return this.space.objects();
	}

	@Override
	public Enumeration<Entry<SpaceObject, List<WeakReference<TrackingVector<SpaceObject>>>>>  getSpaceObjectEntries()
	{
		return this.space.objectEntries();
	}

	@Override
	public List<SpaceObject> getSpaceObjectsByCenterpointWithin(final long[] centerCoordinates, final long minDistance, final long maxDistance)
	{
		final List<SpaceObject> within=new ArrayList<SpaceObject>(1);
		if((centerCoordinates==null)||(centerCoordinates.length!=3))
			return within;
		synchronized(space)
		{
			space.query(within, new BoundedObject.BoundedCube(centerCoordinates, maxDistance));
		}
		if(within.size()<1)
			return within;
		for (final Iterator<SpaceObject> o=within.iterator();o.hasNext();)
		{
			final SpaceObject O=o.next();
			final long dist=getDistanceFrom(O.coordinates(),centerCoordinates);
			if((dist<minDistance)||(dist>maxDistance))
				o.remove();
		}
		return within;
	}

	@Override
	public List<SpaceObject> getSpaceObjectsWithin(final SpaceObject ofObj, final long minDistance, final long maxDistance)
	{
		final List<SpaceObject> within=new ArrayList<SpaceObject>(1);
		if(ofObj==null)
			return within;
		synchronized(space)
		{
			space.query(within, new BoundedObject.BoundedCube(ofObj.coordinates(), maxDistance));
		}
		for (final Iterator<SpaceObject> o=within.iterator();o.hasNext();)
		{
			final SpaceObject O=o.next();
			if(O!=ofObj)
			{
				final long dist=Math.round(Math.abs(getDistanceFrom(O,ofObj) - O.radius() - ofObj.radius()));
				if((dist<minDistance)||(dist>maxDistance))
					o.remove();
			}
		}
		if(within.size()<=1)
			return within;
		Collections.sort(within, new Comparator<SpaceObject>()
		{
			@Override
			public int compare(final SpaceObject o1, final SpaceObject o2)
			{
				final long distTo1=getDistanceFrom(o1,ofObj);
				final long distTo2=getDistanceFrom(o2,ofObj);
				if(distTo1==distTo2)
					return 0;
				return distTo1>distTo2?1:-1;
			}
		});
		return within;
	}

	@Override
	public List<LocationRoom> getLandingPoints(final SpaceObject ship, final Environmental O)
	{
		final List<LocationRoom> rooms=new LinkedList<LocationRoom>();
		final Area A;
		if(O instanceof Area)
			A=(Area)O;
		else
		if(O instanceof BoardableShip)
			A=((BoardableShip)O).getShipArea();
		else
		if(O instanceof Room)
			A=((Room)O).getArea();
		else
			return rooms;
		for(final Enumeration<Room> r=A.getMetroMap();r.hasMoreElements();)
		{
			final Room R2=r.nextElement();
			if(R2 instanceof LocationRoom)
			{
				rooms.add((LocationRoom)R2);
			}
		}
		Collections.sort(rooms,new Comparator<LocationRoom>()
		{
			@Override
			public int compare(final LocationRoom o1, final LocationRoom o2)
			{
				if(o1.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
				{
					if(o2.domainType()!=Room.DOMAIN_OUTDOORS_SPACEPORT)
						return -1;
				}
				else
				if(o2.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
					return 1;
				final long distanceFrom=0;
				if(ship != null)
				{
					final long distanceFrom1=CMLib.map().getDistanceFrom(ship.coordinates(), o1.coordinates());
					final long distanceFrom2=CMLib.map().getDistanceFrom(ship.coordinates(), o1.coordinates());
					if(distanceFrom1 > distanceFrom2)
						return -1;
					if(distanceFrom < distanceFrom2)
						return 1;
					return 0;
				}
				else
					return 0;
			}
		});
		return rooms;
	}

	@Override
	public String createNewExit(Room from, Room room, final int direction)
	{
		Room opRoom=from.rawDoors()[direction];
		if((opRoom!=null)&&(opRoom.roomID().length()==0))
			opRoom=null;
		Room reverseRoom=null;
		if(opRoom!=null)
			reverseRoom=opRoom.rawDoors()[Directions.getOpDirectionCode(direction)];

		if((reverseRoom!=null)&&(reverseRoom==from))
			return "Opposite room already exists and heads this way.  One-way link created.";

		Exit thisExit=null;
		synchronized(("SYNC"+from.roomID()).intern())
		{
			from=getRoom(from);
			if(opRoom!=null)
				from.rawDoors()[direction]=null;

			from.rawDoors()[direction]=room;
			thisExit=from.getRawExit(direction);
			if(thisExit==null)
			{
				thisExit=CMClass.getExit("StdOpenDoorway");
				from.setRawExit(direction,thisExit);
			}
			CMLib.database().DBUpdateExits(from);
		}
		synchronized(("SYNC"+room.roomID()).intern())
		{
			room=getRoom(room);
			if(room.rawDoors()[Directions.getOpDirectionCode(direction)]==null)
			{
				room.rawDoors()[Directions.getOpDirectionCode(direction)]=from;
				room.setRawExit(Directions.getOpDirectionCode(direction),thisExit);
				CMLib.database().DBUpdateExits(room);
			}
		}
		return "";
	}

	@Override
	public int numRooms()
	{
		int total=0;
		for(final Enumeration<Area> e=areas();e.hasMoreElements();)
			total+=e.nextElement().properSize();
		return total;
	}

	@Override
	public boolean sendGlobalMessage(final MOB host, final int category, final CMMsg msg)
	{
		final List<WeakReference<MsgListener>> V=globalHandlers.get(Integer.valueOf(category));
		if(V==null)
			return true;
		synchronized(V)
		{
			try
			{
				MsgListener O=null;
				Environmental E=null;
				for(final WeakReference<MsgListener> W : V)
				{
					O=W.get();
					if(O instanceof Environmental)
					{
						E=(Environmental)O;
						if(!CMLib.flags().isInTheGame(E,true))
						{
							if(!CMLib.flags().isInTheGame(E,false))
								delGlobalHandler(E,category);
						}
						else
						if(!E.okMessage(host,msg))
							return false;
					}
					else
					if(O!=null)
					{
						if(!O.okMessage(host, msg))
							return false;
					}
					else
						V.remove(W);
				}
				for(final WeakReference<MsgListener> W : V)
				{
					O=W.get();
					if(O !=null)
						O.executeMsg(host,msg);
				}
			}
			catch(final java.lang.ArrayIndexOutOfBoundsException xx)
			{
			}
			catch (final Exception x)
			{
				Log.errOut("CMMap", x);
			}
		}
		return true;
	}

	@Override
	public String getExtendedRoomID(final Room R)
	{
		if(R==null)
			return "";
		if(R.roomID().length()>0)
			return R.roomID();
		final Area A=R.getArea();
		if(A==null)
			return "";
		final GridLocale GR=R.getGridParent();
		if((GR!=null)&&(GR.roomID().length()>0))
			return GR.getGridChildCode(R);
		return R.roomID();
	}

	@Override
	public String getDescriptiveExtendedRoomID(final Room room)
	{
		if(room==null)
			return "";
		final String roomID = getExtendedRoomID(room);
		if(roomID.length()>0)
			return roomID;
		final GridLocale gridParentRoom=room.getGridParent();
		if((gridParentRoom!=null)&&(gridParentRoom.roomID().length()==0))
		{
			for(int dir=0;dir<Directions.NUM_DIRECTIONS();dir++)
			{
				final Room attachedRoom = gridParentRoom.rawDoors()[dir];
				if(attachedRoom != null)
				{
					final String attachedRoomID = getExtendedRoomID(attachedRoom);
					if(attachedRoomID.length()>0)
						return CMLib.directions().getFromCompassDirectionName(Directions.getOpDirectionCode(dir))+" "+attachedRoomID;
				}
			}
		}
		Area area=room.getArea();
		if((area==null)&&(gridParentRoom!=null))
			area=gridParentRoom.getArea();
		if(area == null)
			return "";
		return area.Name()+"#?";
	}

	@Override
	public String getApproximateExtendedRoomID(final Room room)
	{
		if(room==null)
			return "";
		Room validRoom = CMLib.tracking().getNearestValidIDRoom(room);
		if(validRoom != null)
		{
			if((validRoom instanceof GridLocale)
			&&(validRoom.roomID()!=null)
			&&(validRoom.roomID().length()>0))
				validRoom=((GridLocale)validRoom).getRandomGridChild();
			return getExtendedRoomID(validRoom);
		}
		if(room.getArea()!=null)
			return room.getArea().Name()+"#?";
		return "";
	}

	@Override
	public String getExtendedTwinRoomIDs(final Room R1,final Room R2)
	{
		final String R1s=getExtendedRoomID(R1);
		final String R2s=getExtendedRoomID(R2);
		if(R1s.compareTo(R2s)>0)
			return R1s+"_"+R2s;
		else
			return R2s+"_"+R1s;
	}

	@Override
	public Room getRoom(final Enumeration<Room> roomSet, final String calledThis)
	{
		try
		{
			if(calledThis==null)
				return null;
			if(calledThis.length()==0)
				return null;
			if(calledThis.endsWith(")"))
			{
				final int child=calledThis.lastIndexOf("#(");
				if(child>1)
				{
					Room R=getRoom(roomSet,calledThis.substring(0,child));
					if((R!=null)&&(R instanceof GridLocale))
					{
						R=((GridLocale)R).getGridChild(calledThis);
						if(R!=null)
							return R;
					}
				}
			}
			Room R=null;
			if(roomSet==null)
			{
				final int x=calledThis.indexOf('#');
				if(x>=0)
				{
					final Area A=getArea(calledThis.substring(0,x));
					if(A!=null)
						R=A.getRoom(calledThis);
					if(R!=null)
						return R;
				}
				for(final Enumeration<Area> e=this.areas();e.hasMoreElements();)
				{
					R = e.nextElement().getRoom(calledThis);
					if(R!=null)
						return R;
				}
				for(final Enumeration<Area> e=shipAreaEnumerator(null);e.hasMoreElements();)
				{
					R = e.nextElement().getRoom(calledThis);
					if(R!=null)
						return R;
				}
			}
			else
			{
				for(final Enumeration<Room> e=roomSet;e.hasMoreElements();)
				{
					R=e.nextElement();
					if(R.roomID().equalsIgnoreCase(calledThis))
						return R;
				}
			}
		}
		catch (final java.util.NoSuchElementException x)
		{
		}
		return null;
	}

	@Override
	public List<Room> findRooms(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean displayOnly, final int timePct)
	{
		final Vector<Room> roomsV=new Vector<Room>();
		if((srchStr.charAt(0)=='#')&&(mob!=null)&&(mob.location()!=null))
			addWorldRoomsLiberally(roomsV,getRoom(mob.location().getArea().Name()+srchStr));
		else
			addWorldRoomsLiberally(roomsV,getRoom(srchStr));
		addWorldRoomsLiberally(roomsV,findRooms(rooms,mob,srchStr,displayOnly,false,timePct));
		return roomsV;
	}

	@Override
	public Room findFirstRoom(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean displayOnly, final int timePct)
	{
		final Vector<Room> roomsV=new Vector<Room>();
		if((srchStr.charAt(0)=='#')&&(mob!=null)&&(mob.location()!=null))
			addWorldRoomsLiberally(roomsV,getRoom(mob.location().getArea().Name()+srchStr));
		else
			addWorldRoomsLiberally(roomsV,getRoom(srchStr));
		if(roomsV.size()>0)
			return roomsV.firstElement();
		addWorldRoomsLiberally(roomsV,findRooms(rooms,mob,srchStr,displayOnly,true,timePct));
		if(roomsV.size()>0)
			return roomsV.firstElement();
		return null;
	}

	public List<Room> findRooms(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean displayOnly, final boolean returnFirst, final int timePct)
	{
		final List<Room> foundRooms=new Vector<Room>();
		Vector<Room> completeRooms=null;
		try
		{
			completeRooms=new XVector<Room>(rooms);
		}
		catch(final Exception nse)
		{
			Log.errOut("CMMap",nse);
			completeRooms=new Vector<Room>();
		}
		final long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);

		Enumeration<Room> enumSet;
		enumSet=completeRooms.elements();
		while(enumSet.hasMoreElements())
		{
			findRoomsByDisplay(mob,enumSet,foundRooms,srchStr,returnFirst,delay);
			if((returnFirst)&&(foundRooms.size()>0))
				return foundRooms;
			if(enumSet.hasMoreElements()) CMLib.s_sleep(1000 - delay);
		}
		if(!displayOnly)
		{
			enumSet=completeRooms.elements();
			while(enumSet.hasMoreElements())
			{
				findRoomsByDesc(mob,enumSet,foundRooms,srchStr,returnFirst,delay);
				if((returnFirst)&&(foundRooms.size()>0))
					return foundRooms;
				if(enumSet.hasMoreElements()) CMLib.s_sleep(1000 - delay);
			}
		}
		return foundRooms;
	}

	protected void findRoomsByDisplay(final MOB mob, final Enumeration<Room> rooms, final List<Room> foundRooms, String srchStr, final boolean returnFirst, final long maxTime)
	{
		final long startTime=System.currentTimeMillis();
		try
		{
			srchStr=srchStr.toUpperCase();
			final boolean useTimer=maxTime>1;
			Room room;
			for(;rooms.hasMoreElements();)
			{
				room=rooms.nextElement();
				if((CMLib.english().containsString(CMStrings.removeColors(room.displayText(mob)),srchStr))
				&&((mob==null)||CMLib.flags().canAccess(mob,room)))
					foundRooms.add(room);
				if((useTimer)&&((System.currentTimeMillis()-startTime)>maxTime))
					return;
			}
		}
		catch (final NoSuchElementException nse)
		{
		}
	}

	protected void findRoomsByDesc(final MOB mob, final Enumeration<Room> rooms, final List<Room> foundRooms, String srchStr, final boolean returnFirst, final long maxTime)
	{
		final long startTime=System.currentTimeMillis();
		try
		{
			srchStr=srchStr.toUpperCase();
			final boolean useTimer=maxTime>1;
			for(;rooms.hasMoreElements();)
			{
				final Room room=rooms.nextElement();
				if((CMLib.english().containsString(CMStrings.removeColors(room.description()),srchStr))
				&&((mob==null)||CMLib.flags().canAccess(mob,room)))
					foundRooms.add(room);
				if((useTimer)&&((System.currentTimeMillis()-startTime)>maxTime))
					return;
			}
		}
		catch (final NoSuchElementException nse)
		{
		}
	}

	@Override
	public List<MOB> findInhabitants(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		return findInhabitants(rooms,mob,srchStr,false,timePct);
	}

	@Override
	public MOB findFirstInhabitant(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		final List<MOB> found=findInhabitants(rooms,mob,srchStr,true,timePct);
		if(found.size()>0)
			return found.get(0);
		return null;
	}

	public List<MOB> findInhabitants(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean returnFirst, final int timePct)
	{
		final Vector<MOB> found=new Vector<MOB>();
		long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);
		if(delay>1000)
			delay=1000;
		final boolean useTimer = delay>1;
		final boolean allRoomsAllowed=(mob==null);
		long startTime=System.currentTimeMillis();
		Room room;
		for(;rooms.hasMoreElements();)
		{
			room=rooms.nextElement();
			if((room != null) && (allRoomsAllowed || CMLib.flags().canAccess(mob,room)))
			{
				found.addAll(room.fetchInhabitants(srchStr));
				if((returnFirst)&&(found.size()>0))
					return found;
			}
			if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
			{
				CMLib.s_sleep(1000 - delay);
				startTime=System.currentTimeMillis();
			}
		}
		return found;
	}

	@Override
	public List<MOB> findInhabitantsFavorExact(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean returnFirst, final int timePct)
	{
		final Vector<MOB> found=new Vector<MOB>();
		final Vector<MOB> exact=new Vector<MOB>();
		long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);
		if(delay>1000)
			delay=1000;
		final boolean useTimer = delay>1;
		final boolean allRoomsAllowed=(mob==null);
		long startTime=System.currentTimeMillis();
		Room room;
		for(;rooms.hasMoreElements();)
		{
			room=rooms.nextElement();
			if((room != null) && (allRoomsAllowed || CMLib.flags().canAccess(mob,room)))
			{
				final MOB M=room.fetchInhabitantExact(srchStr);
				if(M!=null)
				{
					exact.add(M);
					if((returnFirst)&&(exact.size()>0))
						return exact;
				}
				found.addAll(room.fetchInhabitants(srchStr));
			}
			if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
			{
				CMLib.s_sleep(1000 - delay);
				startTime=System.currentTimeMillis();
			}
		}
		if(exact.size()>0)
			return exact;
		if((returnFirst)&&(found.size()>0))
		{
			exact.add(found.get(0));
			return exact;
		}
		return found;
	}

	@Override
	public List<Item> findInventory(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		return findInventory(rooms,mob,srchStr,false,timePct);
	}

	@Override
	public Item findFirstInventory(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		final List<Item> found=findInventory(rooms,mob,srchStr,true,timePct);
		if(found.size()>0)
			return found.get(0);
		return null;
	}

	public List<Item> findInventory(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean returnFirst, final int timePct)
	{
		final List<Item> found=new Vector<Item>();
		long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);
		if(delay>1000)
			delay=1000;
		final boolean useTimer = delay>1;
		long startTime=System.currentTimeMillis();
		MOB M;
		Room room;
		if(rooms==null)
		{
			for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();)
			{
				M=e.nextElement();
				if(M!=null)
					found.addAll(M.findItems(srchStr));
				if((returnFirst)&&(found.size()>0))
					return found;
			}
		}
		else
		for(;rooms.hasMoreElements();)
		{
			room=rooms.nextElement();
			if((room != null) && ((mob==null)||CMLib.flags().canAccess(mob,room)))
			{
				for(int m=0;m<room.numInhabitants();m++)
				{
					M=room.fetchInhabitant(m);
					if(M!=null)
						found.addAll(M.findItems(srchStr));
				}
				if((returnFirst)&&(found.size()>0))
					return found;
			}
			if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
			{
				CMLib.s_sleep(1000 - delay);
				startTime=System.currentTimeMillis();
			}
		}
		return found;
	}

	@Override
	public List<Environmental> findShopStock(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		return findShopStock(rooms,mob,srchStr,false,false,timePct);
	}

	@Override
	public Environmental findFirstShopStock(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		final List<Environmental> found=findShopStock(rooms,mob,srchStr,true,false,timePct);
		if(found.size()>0)
			return found.get(0);
		return null;
	}

	@Override
	public List<Environmental> findShopStockers(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		return findShopStock(rooms,mob,srchStr,false,true,timePct);
	}

	@Override
	public Environmental findFirstShopStocker(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final int timePct)
	{
		final List<Environmental> found=findShopStock(rooms,mob,srchStr,true,true,timePct);
		if(found.size()>0)
			return found.get(0);
		return null;
	}

	public List<Environmental> findShopStock(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean returnFirst, final boolean returnStockers, final int timePct)
	{
		final XVector<Environmental> found=new XVector<Environmental>();
		long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);
		if(delay>1000)
			delay=1000;
		final boolean useTimer = delay>1;
		long startTime=System.currentTimeMillis();
		MOB M=null;
		Item I=null;
		final HashSet<ShopKeeper> stocks=new HashSet<ShopKeeper>(1);
		final HashSet<Area> areas=new HashSet<Area>();
		ShopKeeper SK=null;
		final boolean allRoomsAllowed=(mob==null);
		if(rooms==null)
		{
			for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();)
			{
				M=e.nextElement();
				if(M!=null)
				{
					SK=CMLib.coffeeShops().getShopKeeper(M);
					if((SK!=null)&&(!stocks.contains(SK)))
					{
						stocks.add(SK);
						final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
						if(ei.hasNext())
						{
							if(returnFirst)
								return (returnStockers)?new XVector<Environmental>(M):new XVector<Environmental>(ei);
							if(returnStockers)
								found.add(M);
							else
								found.addAll(ei);
						}
					}
					for(int i=0;i<M.numItems();i++)
					{
						I=M.getItem(i);
						if(I!=null)
						{
							SK=CMLib.coffeeShops().getShopKeeper(I);
							if((SK!=null)&&(!stocks.contains(SK)))
							{
								stocks.add(SK);
								final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
								if(ei.hasNext())
								{
									if(returnFirst)
										return (returnStockers)?new XVector<Environmental>(I):new XVector<Environmental>(ei);
									if(returnStockers)
										found.add(I);
									else
										found.addAll(ei);
								}
							}
						}
					}
				}
				if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
				{
					try
					{
						Thread.sleep(1000 - delay);
						startTime = System.currentTimeMillis();
					}
					catch (final Exception ex)
					{
					}
				}
			}
		}
		else
		for(;rooms.hasMoreElements();)
		{
			final Room room=rooms.nextElement();
			if((room != null) && (allRoomsAllowed||CMLib.flags().canAccess(mob,room)))
			{
				if(!areas.contains(room.getArea()))
					areas.add(room.getArea());
				SK=CMLib.coffeeShops().getShopKeeper(room);
				if((SK!=null)&&(!stocks.contains(SK)))
				{
					stocks.add(SK);
					final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
					if(ei.hasNext())
					{
						if(returnFirst)
							return (returnStockers)?new XVector<Environmental>(room):new XVector<Environmental>(ei);
						if(returnStockers)
							found.add(room);
						else
							found.addAll(ei);
					}
				}
				for(int m=0;m<room.numInhabitants();m++)
				{
					M=room.fetchInhabitant(m);
					if(M!=null)
					{
						SK=CMLib.coffeeShops().getShopKeeper(M);
						if((SK!=null)&&(!stocks.contains(SK)))
						{
							stocks.add(SK);
							final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
							if(ei.hasNext())
							{
								if(returnFirst)
									return (returnStockers)?new XVector<Environmental>(M):new XVector<Environmental>(ei);
								if(returnStockers)
									found.add(M);
								else
									found.addAll(ei);
							}
						}
					}
				}
				for(int i=0;i<room.numItems();i++)
				{
					I=room.getItem(i);
					if(I!=null)
					{
						SK=CMLib.coffeeShops().getShopKeeper(I);
						if((SK!=null)&&(!stocks.contains(SK)))
						{
							stocks.add(SK);
							final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
							if(ei.hasNext())
							{
								if(returnFirst)
									return (returnStockers)?new XVector<Environmental>(I):new XVector<Environmental>(ei);
								if(returnStockers)
									found.add(I);
								else
									found.addAll(ei);
							}
						}
					}
				}
			}
			if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
			{
				CMLib.s_sleep(1000 - delay);
				startTime=System.currentTimeMillis();
			}
		}
		for (final Area A : areas)
		{
			SK=CMLib.coffeeShops().getShopKeeper(A);
			if((SK!=null)&&(!stocks.contains(SK)))
			{
				stocks.add(SK);
				final Iterator<Environmental> ei=SK.getShop().getStoreInventory(srchStr);
				if(ei.hasNext())
				{
					if(returnFirst)
						return (returnStockers)?new XVector<Environmental>(A):new XVector<Environmental>(ei);
					if(returnStockers)
						found.add(A);
					else
						found.addAll(ei);
				}
			}
		}
		return found;
	}

	@Override
	public List<Item> findRoomItems(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean anyItems, final int timePct)
	{
		return findRoomItems(rooms,mob,srchStr,anyItems,false,timePct);
	}

	@Override
	public Item findFirstRoomItem(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean anyItems, final int timePct)
	{
		final List<Item> found=findRoomItems(rooms,mob,srchStr,anyItems,true,timePct);
		if(found.size()>0)
			return found.get(0);
		return null;
	}

	protected List<Item> findRoomItems(final Enumeration<Room> rooms, final MOB mob, final String srchStr, final boolean anyItems, final boolean returnFirst, final int timePct)
	{
		final Vector<Item> found=new Vector<Item>(); // ultimate return value
		long delay=Math.round(CMath.s_pct(timePct+"%") * 1000);
		if(delay>1000)
			delay=1000;
		final boolean useTimer = delay>1;
		long startTime=System.currentTimeMillis();
		final boolean allRoomsAllowed=(mob==null);
		Room room;
		for(;rooms.hasMoreElements();)
		{
			room=rooms.nextElement();
			if((room != null) && (allRoomsAllowed||CMLib.flags().canAccess(mob,room)))
			{
				found.addAll(anyItems?room.findItems(srchStr):room.findItems(null,srchStr));
				if((returnFirst)&&(found.size()>0))
					return found;
			}
			if((useTimer)&&((System.currentTimeMillis()-startTime)>delay))
			{
				CMLib.s_sleep(1000 - delay);
				startTime=System.currentTimeMillis();
			}
		}
		return found;
	}

	@Override
	public Room getRoom(final Room room)
	{
		if(room==null)
			return null;
		if(room.amDestroyed())
			return getRoom(getExtendedRoomID(room));
		return room;
	}

	@Override
	public Room getRoom(final String calledThis)
	{
		return getRoom(null,calledThis);
	}

	@Override
	public Enumeration<Room> rooms()
	{
		return new AreasRoomsEnumerator(areasPlusShips(), false);
	}

	@Override
	public Enumeration<Room> roomsFilled()
	{
		return new AreasRoomsEnumerator(areasPlusShips(), true);
	}

	@Override
	public Enumeration<MOB> worldMobs()
	{
		return new RoomMobsEnumerator(roomsFilled());
	}

	@Override
	public Enumeration<Item> worldRoomItems()
	{
		return new RoomItemsEnumerator(roomsFilled(), false);
	}

	@Override
	public Enumeration<Item> worldEveryItems()
	{
		return new RoomItemsEnumerator(roomsFilled(),true);
	}

	@Override
	public Room getRandomRoom()
	{
		Room R=null;
		int numRooms=-1;
		for(int i=0;i<1000 && ((R==null)&&((numRooms=numRooms())>0));i++)
		{
			try
			{
				final int which=CMLib.dice().roll(1,numRooms,-1);
				int total=0;
				for(final Enumeration<Area> e=areas();e.hasMoreElements();)
				{
					final Area A=e.nextElement();
					if(which<(total+A.properSize()))
					{
						R = A.getRandomProperRoom();
						break;
					}
					total+=A.properSize();
				}
			}
			catch (final NoSuchElementException e)
			{
				if(i > 998)
					Log.errOut(e);
			}
		}
		return R;
	}

	public int numDeities()
	{
		return deitiesList.size();
	}

	protected void addDeity(final Deity newOne)
	{
		if (!deitiesList.contains(newOne))
			deitiesList.add(newOne);
	}

	protected void delDeity(final Deity oneToDel)
	{
		if (deitiesList.contains(oneToDel))
			deitiesList.remove(oneToDel);
	}

	@Override
	public Deity getDeity(final String calledThis)
	{
		if((calledThis==null)||(calledThis.length()==0))
			return null;
		return deitiesList.find(calledThis);
	}

	@Override
	public Enumeration<Deity> deities()
	{
		return new IteratorEnumeration<Deity>(deitiesList.iterator());
	}

	@Override
	public int numShips()
	{
		return shipList.size();
	}

	protected void addShip(final BoardableShip newOne)
	{
		if (!shipList.contains(newOne))
		{
			shipList.add(newOne);
			final Area area=newOne.getShipArea();
			if((area!=null)&&(area.getAreaState()==Area.State.ACTIVE))
				area.setAreaState(Area.State.ACTIVE);
		}
	}

	protected void delShip(final BoardableShip oneToDel)
	{
		if(oneToDel!=null)
		{
			shipList.remove(oneToDel);
			final Item shipI = oneToDel.getShipItem();
			if(shipI instanceof BoardableShip)
			{
				final BoardableShip boardableShipI = (BoardableShip)shipI;
				shipList.remove(boardableShipI);
			}
			final Area area=oneToDel.getShipArea();
			if(area!=null)
			{
				if(area instanceof BoardableShip)
				{
					final BoardableShip boardableShipA = (BoardableShip)area;
					shipList.remove(boardableShipA);
				}
				area.setAreaState(Area.State.STOPPED);
			}
		}
	}

	@Override
	public BoardableShip getShip(final String calledThis)
	{
		for (final BoardableShip S : shipList)
		{
			if (S.Name().equalsIgnoreCase(calledThis))
				return S;
		}
		return null;
	}

	@Override
	public BoardableShip findShip(final String s, final boolean exactOnly)
	{
		return (BoardableShip)CMLib.english().fetchEnvironmental(shipList, s, exactOnly);
	}

	@Override
	public Enumeration<BoardableShip> ships()
	{
		return new IteratorEnumeration<BoardableShip>(shipList.iterator());
	}

	public Enumeration<Room> shipsRoomEnumerator(final Area inA)
	{
		return new Enumeration<Room>()
		{
			private Enumeration<Room> cur = null;
			private Enumeration<Area> cA = shipAreaEnumerator(inA);

			@Override
			public boolean hasMoreElements()
			{
				boolean hasMore = (cur != null) && cur.hasMoreElements();
				while(!hasMore)
				{
					if((cA == null)||(!cA.hasMoreElements()))
					{
						cur=null;
						cA = null;
						return false;
					}
					cur = cA.nextElement().getProperMap();
					hasMore = (cur != null) && cur.hasMoreElements();
				}
				return hasMore;
			}

			@Override
			public Room nextElement()
			{
				if(!hasMoreElements())
					throw new NoSuchElementException();
				return cur.nextElement();
			}
		};
	}

	public Enumeration<Area> shipAreaEnumerator(final Area inA)
	{
		return new Enumeration<Area>()
		{
			private volatile Area nextArea=null;
			private volatile Enumeration<BoardableShip> shipsEnum=ships();

			@Override
			public boolean hasMoreElements()
			{
				while(nextArea == null)
				{
					if((shipsEnum==null)||(!shipsEnum.hasMoreElements()))
					{
						shipsEnum=null;
						return false;
					}
					final BoardableShip ship=shipsEnum.nextElement();
					if((ship!=null)&&(ship.getShipArea()!=null))
					{
						if((inA==null)||(areaLocation(ship)==inA))
							nextArea=ship.getShipArea();
					}
				}
				return (nextArea != null);
			}

			@Override
			public Area nextElement()
			{
				if(!hasMoreElements())
					throw new NoSuchElementException();
				final Area A=nextArea;
				this.nextArea=null;
				return A;
			}
		};
	}

	public int numPostOffices()
	{
		return postOfficeList.size();
	}

	protected void addPostOffice(final PostOffice newOne)
	{
		if(!postOfficeList.contains(newOne))
			postOfficeList.add(newOne);
	}

	protected void delPostOffice(final PostOffice oneToDel)
	{
		postOfficeList.remove(oneToDel);
	}

	@Override
	public PostOffice getPostOffice(final String chain, final String areaNameOrBranch)
	{
		final boolean anyArea = areaNameOrBranch.equalsIgnoreCase("*");
		for (final PostOffice P : postOfficeList)
		{
			if((P.postalChain().equalsIgnoreCase(chain))
			&&((anyArea)||(P.postalBranch().equalsIgnoreCase(areaNameOrBranch))))
				return P;
		}

		final Area A=findArea(areaNameOrBranch);
		if(A==null)
			return null;

		for (final PostOffice P : postOfficeList)
		{
			if((P.postalChain().equalsIgnoreCase(chain))
			&&(getStartArea(P)==A))
				return P;
		}
		return null;
	}

	@Override
	public Enumeration<PostOffice> postOffices()
	{
		return new IteratorEnumeration<PostOffice>(postOfficeList.iterator());
	}

	@Override
	public Enumeration<Auctioneer> auctionHouses()
	{
		return new IteratorEnumeration<Auctioneer>(auctionHouseList.iterator());
	}

	public int numAuctionHouses()
	{
		return auctionHouseList.size();
	}

	protected void addAuctionHouse(final Auctioneer newOne)
	{
		if (!auctionHouseList.contains(newOne))
		{
			auctionHouseList.add(newOne);
		}
	}

	protected void delAuctionHouse(final Auctioneer oneToDel)
	{
		auctionHouseList.remove(oneToDel);
	}

	@Override
	public Auctioneer getAuctionHouse(final String chain, final String areaNameOrBranch)
	{
		for (final Auctioneer C : auctionHouseList)
		{
			if((C.auctionHouse().equalsIgnoreCase(chain))
			&&(C.auctionHouse().equalsIgnoreCase(areaNameOrBranch)))
				return C;
		}

		final Area A=findArea(areaNameOrBranch);
		if(A==null)
			return null;

		for (final Auctioneer C : auctionHouseList)
		{
			if((C.auctionHouse().equalsIgnoreCase(chain))
			&&(getStartArea(C)==A))
				return C;
		}

		return null;
	}

	public int numBanks()
	{
		return bankList.size();
	}

	protected void addBank(final Banker newOne)
	{
		if (!bankList.contains(newOne))
			bankList.add(newOne);
	}

	protected void delBank(final Banker oneToDel)
	{
		bankList.remove(oneToDel);
	}

	@Override
	public Banker getBank(final String chain, final String areaNameOrBranch)
	{
		for (final Banker B : bankList)
		{
			if((B.bankChain().equalsIgnoreCase(chain))
			&&(B.bankChain().equalsIgnoreCase(areaNameOrBranch)))
				return B;
		}

		final Area A=findArea(areaNameOrBranch);
		if(A==null)
			return null;

		for (final Banker B : bankList)
		{
			if((B.bankChain().equalsIgnoreCase(chain))
			&&(getStartArea(B)==A))
				return B;
		}
		return null;
	}

	@Override
	public Enumeration<Banker> banks()
	{
		return new IteratorEnumeration<Banker>(bankList.iterator());
	}

	@Override
	public Iterator<String> bankChains(final Area AreaOrNull)
	{
		final HashSet<String> H=new HashSet<String>();
		for (final Banker B : bankList)
		{
			if((!H.contains(B.bankChain()))
			&&((AreaOrNull==null)
				||(getStartArea(B)==AreaOrNull)
				||(AreaOrNull.isChild(getStartArea(B)))))
					H.add(B.bankChain());
		}
		return H.iterator();
	}

	protected Set<Places> getHolyPlaces(final String deityName)
	{
		if(deityName==null)
			return new TreeSet<Places>();
		final String udName=deityName.toUpperCase().trim();
		if(!holyPlaces.containsKey(udName))
			holyPlaces.put(udName, new TreeSet<Places>(Places.placeComparator));
		return holyPlaces.get(udName);
	}

	@Override
	public void registerHolyPlace(final String deityName, final Places newOne)
	{
		if(newOne != null)
		{
			final Set<Places> holyPlaces = getHolyPlaces(deityName);
			if (!holyPlaces.contains(newOne))
				holyPlaces.add(newOne);
		}
	}

	@Override
	public void deregisterHolyPlace(final String deityName, final Places newOne)
	{
		if(newOne != null)
		{
			final Set<Places> holyPlaces = getHolyPlaces(deityName);
			holyPlaces.remove(newOne);
		}
	}

	@Override
	public Enumeration<Places> holyPlaces(final String deityName)
	{
		final Set<Places> holyPlaces = getHolyPlaces(deityName);
		final ArrayList<Places> placesCopy=new ArrayList<Places>(holyPlaces.size());
		synchronized(holyPlaces)
		{
			for(final Iterator<Places> i=holyPlaces.iterator();i.hasNext();)
			{
				final Places place = i.next();
				if(place.amDestroyed())
					i.remove();
				else
					placesCopy.add(place);
			}
		}
		return new IteratorEnumeration<Places>(placesCopy.iterator());
	}

	@Override
	public int numLibraries()
	{
		return libraryList.size();
	}

	protected void addLibrary(final Librarian newOne)
	{
		if (!libraryList.contains(newOne))
			libraryList.add(newOne);
	}

	protected void delLibrary(final Librarian oneToDel)
	{
		libraryList.remove(oneToDel);
	}

	@Override
	public Librarian getLibrary(final String chain, final String areaNameOrBranch)
	{
		for (final Librarian B : libraryList)
		{
			if((B.libraryChain().equalsIgnoreCase(chain))
			&&(B.libraryChain().equalsIgnoreCase(areaNameOrBranch)))
				return B;
		}

		final Area A=findArea(areaNameOrBranch);
		if(A==null)
			return null;

		for (final Librarian B : libraryList)
		{
			if((B.libraryChain().equalsIgnoreCase(chain))
			&&(getStartArea(B)==A))
				return B;
		}
		return null;
	}

	@Override
	public Enumeration<Librarian> libraries()
	{
		return new IteratorEnumeration<Librarian>(libraryList.iterator());
	}

	@Override
	public Iterator<String> libraryChains(final Area AreaOrNull)
	{
		final HashSet<String> H=new HashSet<String>();
		for (final Librarian B : libraryList)
		{
			if((!H.contains(B.libraryChain()))
			&&((AreaOrNull==null)
				||(getStartArea(B)==AreaOrNull)
				||(AreaOrNull.isChild(getStartArea(B)))))
					H.add(B.libraryChain());
		}
		return H.iterator();
	}

	@Override
	public void renameRooms(final Area A, final String oldName, final List<Room> allMyDamnRooms)
	{
		final List<Room> onesToRenumber=new Vector<Room>();
		for(Room R : allMyDamnRooms)
		{
			synchronized(("SYNC"+R.roomID()).intern())
			{
				R=getRoom(R);
				R.setArea(A);
				if(oldName!=null)
				{
					if(R.roomID().toUpperCase().startsWith(oldName.toUpperCase()+"#"))
					{
						Room R2=A.getRoom(A.Name()+"#"+R.roomID().substring(oldName.length()+1));
						if(R2 == null)
							R2=getRoom(A.Name()+"#"+R.roomID().substring(oldName.length()+1));
						if((R2==null)||(!R2.roomID().startsWith(A.Name()+"#")))
						{
							final String oldID=R.roomID();
							R.setRoomID(A.Name()+"#"+R.roomID().substring(oldName.length()+1));
							CMLib.database().DBReCreate(R,oldID);
						}
						else
							onesToRenumber.add(R);
					}
					else
						CMLib.database().DBUpdateRoom(R);
				}
			}
		}
		if(oldName!=null)
		{
			for(final Room R: onesToRenumber)
			{
				final String oldID=R.roomID();
				R.setRoomID(A.getNewRoomID(R,-1));
				CMLib.database().DBReCreate(R,oldID);
			}
		}
	}

	@Override
	public int getRoomDir(final Room from, final Room to)
	{
		if((from==null)||(to==null))
			return -1;
		for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
		{
			if(from.getRoomInDir(d)==to)
				return d;
		}
		return -1;
	}

	@Override
	public Area getTargetArea(final Room from, final Exit to)
	{
		final Room R=getTargetRoom(from, to);
		if(R==null)
			return null;
		return R.getArea();
	}

	@Override
	public Room getTargetRoom(final Room from, final Exit to)
	{
		if((from==null)||(to==null))
			return null;
		final int d=getExitDir(from, to);
		if(d<0)
			return null;
		return from.getRoomInDir(d);
	}

	@Override
	public int getExitDir(final Room from, final Exit to)
	{
		if((from==null)||(to==null))
			return -1;
		for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
		{
			if(from.getExitInDir(d)==to)
				return d;
			if(from.getRawExit(d)==to)
				return d;
			if(from.getReverseExit(d)==to)
				return d;
		}
		return -1;
	}

	@Override
	public Room findConnectingRoom(final Room room)
	{
		if(room==null)
			return null;
		Room R=null;
		final Vector<Room> otherChoices=new Vector<Room>();
		for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
		{
			R=room.getRoomInDir(d);
			if(R!=null)
			{
				for(int d1=Directions.NUM_DIRECTIONS()-1;d1>=0;d1--)
				{
					if(R.getRoomInDir(d1)==room)
					{
						if(R.getArea()==room.getArea())
							return R;
						otherChoices.addElement(R);
					}
				}
			}
		}
		for(final Enumeration<Room> e=rooms();e.hasMoreElements();)
		{
			R=e.nextElement();
			if(R==room)
				continue;
			for(int d1=Directions.NUM_DIRECTIONS()-1;d1>=0;d1--)
			{
				if(R.getRoomInDir(d1)==room)
				{
					if(R.getArea()==room.getArea())
						return R;
					otherChoices.addElement(R);
				}
			}
		}
		if(otherChoices.size()>0)
			return otherChoices.firstElement();
		return null;
	}

	@Override
	public boolean isClearableRoom(final Room R)
	{
		if((R==null)||(R.amDestroyed()))
			return true;
		MOB M=null;
		Room sR=null;
		for(int i=0;i<R.numInhabitants();i++)
		{
			M=R.fetchInhabitant(i);
			if(M==null)
				continue;
			sR=M.getStartRoom();
			if((sR!=null)
			&&(sR != R)
			&&(!sR.roomID().equals(R.roomID()))
			&&(!sR.amDestroyed()))
			{
				CMLib.tracking().wanderAway(M, false, true);
				if(M.location()==R)
					return false;
			}
			if(M.session()!=null)
				return false;
		}
		Item I=null;
		for(int i=0;i<R.numItems();i++)
		{
			I=R.getItem(i);
			if((I!=null)
			&&((I.expirationDate()!=0)
				||((I instanceof DeadBody)&&(((DeadBody)I).isPlayerCorpse()))
				||((I instanceof PrivateProperty)&&(((PrivateProperty)I).getOwnerName().length()>0))))
					return false;
		}
		for(final Enumeration<Ability> a=R.effects();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if((A!=null)&&(!A.isSavable()))
				return false;
		}
		return true;
	}

	@Override
	public boolean explored(final Room R)
	{
		if((R==null)
		||(CMath.bset(R.phyStats().sensesMask(),PhyStats.SENSE_ROOMUNEXPLORABLE))
		||(R.getArea()==null))
			return false;
		return false;
	}

	public class AreasRoomsEnumerator implements Enumeration<Room>
	{
		private final Enumeration<Area>		curAreaEnumeration;
		private final boolean				addSkys;
		private volatile Enumeration<Room>	curRoomEnumeration	= null;

		public AreasRoomsEnumerator(final Enumeration<Area> curAreaEnumeration, final boolean includeSkys)
		{
			addSkys = includeSkys;
			this.curAreaEnumeration=curAreaEnumeration;
		}

		@Override
		public boolean hasMoreElements()
		{
			boolean hasMore = (curRoomEnumeration!=null)&&(curRoomEnumeration.hasMoreElements());
			while(!hasMore)
			{
				if((curAreaEnumeration == null)||(!curAreaEnumeration.hasMoreElements()))
				{
					curRoomEnumeration = null;
					return false;
				}
				if(addSkys)
					curRoomEnumeration=curAreaEnumeration.nextElement().getFilledProperMap();
				else
					curRoomEnumeration=curAreaEnumeration.nextElement().getProperMap();
				hasMore = (curRoomEnumeration!=null)&&(curRoomEnumeration.hasMoreElements());
			}
			return hasMore;
		}

		@Override
		public Room nextElement()
		{
			if(!hasMoreElements())
				throw new NoSuchElementException();
			return curRoomEnumeration.nextElement();
		}
	}

	public class RoomMobsEnumerator implements Enumeration<MOB>
	{
		private final Enumeration<Room>		curRoomEnumeration;
		private volatile Enumeration<MOB>	curMobEnumeration	= null;

		public RoomMobsEnumerator(final Enumeration<Room> curRoomEnumeration)
		{
			this.curRoomEnumeration=curRoomEnumeration;
		}

		@Override
		public boolean hasMoreElements()
		{
			boolean hasMore = (curMobEnumeration!=null)&&(curMobEnumeration.hasMoreElements());
			while(!hasMore)
			{
				if((curRoomEnumeration == null)||(!curRoomEnumeration.hasMoreElements()))
				{
					curMobEnumeration = null;
					return false;
				}
				curMobEnumeration=curRoomEnumeration.nextElement().inhabitants();
				hasMore = (curMobEnumeration!=null)&&(curMobEnumeration.hasMoreElements());
			}
			return hasMore;
		}

		@Override
		public MOB nextElement()
		{
			if(!hasMoreElements())
				throw new NoSuchElementException();
			return curMobEnumeration.nextElement();
		}
	}

	public class RoomItemsEnumerator implements Enumeration<Item>
	{
		private final Enumeration<Room>		curRoomEnumeration;
		private final boolean				includeMobItems;
		private volatile Enumeration<Item>	curItemEnumeration	= null;

		public RoomItemsEnumerator(final Enumeration<Room> curRoomEnumeration, final boolean includeMobItems)
		{
			this.curRoomEnumeration=curRoomEnumeration;
			this.includeMobItems=includeMobItems;
		}

		@Override
		public boolean hasMoreElements()
		{
			boolean hasMore = (curItemEnumeration!=null)&&(curItemEnumeration.hasMoreElements());
			while(!hasMore)
			{
				if((curRoomEnumeration == null)||(!curRoomEnumeration.hasMoreElements()))
				{
					curItemEnumeration = null;
					return false;
				}
				if(includeMobItems)
					curItemEnumeration=curRoomEnumeration.nextElement().itemsRecursive();
				else
					curItemEnumeration=curRoomEnumeration.nextElement().items();
				hasMore = (curItemEnumeration!=null)&&(curItemEnumeration.hasMoreElements());
			}
			return hasMore;
		}

		@Override
		public Item nextElement()
		{
			if(!hasMoreElements())
				throw new NoSuchElementException();
			return curItemEnumeration.nextElement();
		}
	}

	@Override
	public void obliterateMapRoom(final Room deadRoom)
	{
		obliterateRoom(deadRoom,true);
	}

	@Override
	public void destroyRoomObject(final Room deadRoom)
	{
		obliterateRoom(deadRoom,false);
	}

	protected void obliterateRoom(final Room deadRoom, final boolean includeDB)
	{
		for(final Enumeration<Ability> a=deadRoom.effects();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if(A!=null)
			{
				A.unInvoke();
				deadRoom.delEffect(A);
			}
		}
		try
		{
			final List<Pair<Room,Integer>> roomsToDo=new LinkedList<Pair<Room,Integer>>();
			for(final Enumeration<Room> r=rooms();r.hasMoreElements();)
			{
				final Room R=getRoom(r.nextElement());
				if(R!=null)
				{
					for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
					{
						final Room thatRoom=R.rawDoors()[d];
						if(thatRoom==deadRoom)
							roomsToDo.add(new Pair<Room,Integer>(R,Integer.valueOf(d)));
					}
				}
			}
			for(final Pair<Room,Integer> p : roomsToDo)
			{
				final Room R=p.first;
				final int d=p.second.intValue();
				synchronized(("SYNC"+R.roomID()).intern())
				{
					R.rawDoors()[d]=null;
					if((R.getRawExit(d)!=null)&&(R.getRawExit(d).isGeneric()))
					{
						final Exit GE=R.getRawExit(d);
						GE.setTemporaryDoorLink(deadRoom.roomID());
					}
					if(includeDB)
						CMLib.database().DBUpdateExits(R);
				}
			}
		}
		catch (final NoSuchElementException e)
		{
		}
		emptyRoom(deadRoom,null,true);
		deadRoom.destroy();
		if(deadRoom instanceof GridLocale)
			((GridLocale)deadRoom).clearGrid(null);
		if(includeDB)
			CMLib.database().DBDeleteRoom(deadRoom);
	}

	@Override
	public void emptyAreaAndDestroyRooms(final Area area)
	{
		for(final Enumeration<Ability> a=area.effects();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if(A!=null)
			{
				A.unInvoke();
				area.delEffect(A);
			}
		}
		for(final Enumeration<Room> e=area.getProperMap();e.hasMoreElements();)
		{
			final Room R=e.nextElement();
			emptyRoom(R,null,true);
			R.destroy();
		}
	}

	@Override
	public Room roomLocation(final Environmental E)
	{
		if(E==null)
			return null;
		if((E instanceof Area)&&(!((Area)E).isProperlyEmpty()))
			return ((Area)E).getRandomProperRoom();
		else
		if(E instanceof Room)
			return (Room)E;
		else
		if(E instanceof MOB)
			return ((MOB)E).location();
		else
		if((E instanceof Item)&&(((Item)E).owner() instanceof Room))
			return (Room)((Item)E).owner();
		else
		if((E instanceof Item)&&(((Item)E).owner() instanceof MOB))
			return ((MOB)((Item)E).owner()).location();
		else
		if(E instanceof Ability)
			return roomLocation(((Ability)E).affecting());
		else
		if(E instanceof Exit)
			return roomLocation(((Exit)E).lastRoomUsedFrom(null));
		return null;
	}

	@Override
	public Area getStartArea(final Environmental E)
	{
		if(E instanceof Area)
			return (Area)E;
		final Room R=getStartRoom(E);
		if(R==null)
			return null;
		return R.getArea();
	}

	@Override
	public Room getStartRoom(final Environmental E)
	{
		if(E ==null)
			return null;
		if(E instanceof MOB)
			return ((MOB)E).getStartRoom();
		if(E instanceof Item)
		{
			if(((Item)E).owner() instanceof MOB)
				return getStartRoom(((Item)E).owner());
			if(CMLib.flags().isGettable((Item)E))
				return null;
		}
		if(E instanceof Ability)
			return getStartRoom(((Ability)E).affecting());
		if((E instanceof Area)&&(!((Area)E).isProperlyEmpty()))
			return ((Area)E).getRandomProperRoom();
		if(E instanceof Room)
			return (Room)E;
		return roomLocation(E);
	}

	@Override
	public ThreadGroup getOwnedThreadGroup(final CMObject E)
	{
		final Area area=areaLocation(E);
		if(area != null)
		{
			final int theme=area.getTheme();
			if((theme>0)&&(theme<Area.THEME_NAMES.length))
				return CMProps.getPrivateOwner(Area.THEME_NAMES[theme]+"AREAS");
		}
		return null;
	}

	@Override
	public Area areaLocation(final CMObject E)
	{
		if(E==null)
			return null;
		if(E instanceof Area)
			return (Area)E;
		else
		if(E instanceof Room)
			return ((Room)E).getArea();
		else
		if(E instanceof MOB)
			return areaLocation(((MOB)E).location());
		else
		if(E instanceof Item)
			return areaLocation(((Item) E).owner());
		else
		if((E instanceof Ability)&&(((Ability)E).affecting()!=null))
			return areaLocation(((Ability)E).affecting());
		else
		if(E instanceof Exit)
			return areaLocation(((Exit)E).lastRoomUsedFrom(null));
		return null;
	}

	@Override
	public Room getSafeRoomToMovePropertyTo(final Room room, final PrivateProperty I)
	{
		if(I instanceof BoardableShip)
		{
			final Room R=getRoom(((BoardableShip)I).getHomePortID());
			if((R!=null)&&(R!=room)&&(!R.amDestroyed()))
				return R;
		}
		if(room != null)
		{
			Room R=null;
			if(room.getGridParent()!=null)
			{
				R=getRoom(room.getGridParent());
				if((R!=null)&&(R!=room)&&(!R.amDestroyed())&&(R.roomID().length()>0))
					return R;
			}
			for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
			{
				R=getRoom(room.getRoomInDir(d));
				if((R!=null)&&(R!=room)&&(!R.amDestroyed())&&(R.roomID().length()>0))
					return R;
			}
			if(room.getGridParent()!=null)
			{
				for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
				{
					R=getRoom(room.getGridParent().getRoomInDir(d));
					if((R!=null)&&(R!=room)&&(!R.amDestroyed())&&(R.roomID().length()>0))
						return R;
				}
			}
			final Area A=room.getArea();
			if(A!=null)
			{
				for(int i=0;i<A.numberOfProperIDedRooms();i++)
				{
					R=getRoom(A.getRandomProperRoom());
					if((R!=null)&&(R!=room)&&(!R.amDestroyed())&&(R.roomID().length()>0))
						return R;
				}
			}
		}
		for(int i=0;i<100;i++)
		{
			final Room R=getRoom(this.getRandomRoom());
			if((R!=null)&&(R!=room)&&(!R.amDestroyed())&&(R.roomID().length()>0))
				return R;
		}
		return null;
	}

	@Override
	public void emptyRoom(final Room room, final Room toRoom, final boolean clearPlayers)
	{
		if(room==null)
			return;
		// this will empty grid rooms so that
		// the code below can delete them or whatever.
		if(room instanceof GridLocale)
		{
			for(final Iterator<Room> r=((GridLocale)room).getExistingRooms();r.hasNext();)
				emptyRoom(r.next(), toRoom, clearPlayers);
		}
		// this will empty skys and underwater of mobs so that
		// the code below can delete them or whatever.
		room.clearSky();
		if(toRoom != null)
		{
			for(final Enumeration<MOB> i=room.inhabitants();i.hasMoreElements();)
			{
				final MOB M=i.nextElement();
				if(M!=null)
					toRoom.bringMobHere(M,false);
			}
		}
		else
		if(clearPlayers)
		{
			for(final Enumeration<MOB> i=room.inhabitants();i.hasMoreElements();)
			{
				final MOB M=i.nextElement();
				if((M!=null) && (M.isPlayer()))
					M.getStartRoom().bringMobHere(M,true);
			}
		}
		for(final Enumeration<MOB> i=room.inhabitants();i.hasMoreElements();)
		{
			final MOB M=i.nextElement();
			if((M!=null)
			&&(!M.isPlayer())
			&&(M.isSavable()) // this is almost certainly to protect Quest mobs, which are just about the only unsavable things.
			&&((M.amFollowing()==null)||(!M.amFollowing().isPlayer())))
			{
				final Room startRoom = M.getStartRoom();
				final Area startArea = (startRoom == null) ? null : startRoom.getArea();
				if((startRoom==null)
				||(startRoom==room)
				||(startRoom.amDestroyed())
				||(startArea==null)
				||(startArea.amDestroyed())
				||(startRoom.ID().length()==0))
					M.destroy();
				else
					M.getStartRoom().bringMobHere(M,false);
			}
		}

		Item I=null;
		if(toRoom != null)
		{
			for(final Enumeration<Item> i=room.items();i.hasMoreElements();)
			{
				I=i.nextElement();
				if(I!=null)
					toRoom.moveItemTo(I,ItemPossessor.Expire.Player_Drop);
			}
		}
		else
		{
			for(final Enumeration<Item> i=room.items();i.hasMoreElements();)
			{
				I=i.nextElement();
				if(I != null)
				{
					if((I instanceof PrivateProperty)
					&&((((PrivateProperty)I).getOwnerName().length()>0)))
					{
						final Room R=getSafeRoomToMovePropertyTo(room, (PrivateProperty)I);
						if((R!=null)
						&&(R!=room))
							R.moveItemTo(I,ItemPossessor.Expire.Player_Drop);
					}
					else
						I.destroy();
				}
			}
		}
		room.clearSky();
		 // clear debri only clears things by their start rooms, not location, so only roomid matters.
		if(room.roomID().length()>0)
			CMLib.threads().clearDebri(room,0);
		if(room instanceof GridLocale)
		{
			for(final Iterator<Room> r=((GridLocale)room).getExistingRooms();r.hasNext();)
				emptyRoom(r.next(), toRoom, clearPlayers);
		}
	}

	@Override
	public void obliterateMapArea(final Area A)
	{
		obliterateArea(A,true);
		for(final Enumeration<Area> a=CMLib.map().areas();a.hasMoreElements();)
		{
			final Area A2=a.nextElement();
			if((A2!=null)
			&&(A2.isSavable())
			&&(A2.isParent(A)||A2.isChild(A)))
			{
				A2.removeParent(A);
				A2.removeChild(A);
				CMLib.database().DBUpdateArea(A2.Name(), A2);
			}
		}
	}

	@Override
	public void destroyAreaObject(final Area A)
	{
		obliterateArea(A,false);
	}

	protected void obliterateArea(final Area A, final boolean includeDB)
	{
		if(A==null)
			return;
		A.setAreaState(Area.State.STOPPED);
		if(A instanceof SpaceShip)
			CMLib.tech().unregisterAllElectronics(CMLib.tech().getElectronicsKey(A));
		final List<Room> allRooms=new LinkedList<Room>();
		for(int i=0;i<2;i++)
		{
			for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
			{
				final Room R=e.nextElement();
				if(R!=null)
				{
					allRooms.add(R);
					emptyRoom(R,null,false);
					R.clearSky();
				}
			}
		}
		if(includeDB)
			CMLib.database().DBDeleteAreaAndRooms(A);
		for(final Room R : allRooms)
			obliterateRoom(R,includeDB);
		delArea(A);
		A.destroy(); // why not?
	}

	public CMMsg resetMsg=null;

	@Override
	public void resetRoom(final Room room)
	{
		resetRoom(room,false);
	}

	@Override
	public void resetRoom(Room room, final boolean rebuildGrids)
	{
		if(room==null)
			return;
		if(room.roomID().length()==0)
			return;
		synchronized(("SYNC"+room.roomID()).intern())
		{
			room=getRoom(room);
			if((rebuildGrids)&&(room instanceof GridLocale))
				((GridLocale)room).clearGrid(null);
			final boolean mobile=room.getMobility();
			try
			{
				room.toggleMobility(false);
				if(resetMsg==null)
					resetMsg=CMClass.getMsg(CMClass.sampleMOB(),room,CMMsg.MSG_ROOMRESET,null);
				resetMsg.setTarget(room);
				room.executeMsg(room,resetMsg);
				if(room.isSavable())
					emptyRoom(room,null,false);
				for(final Enumeration<Ability> a=room.effects();a.hasMoreElements();)
				{
					final Ability A=a.nextElement();
					if((A!=null)&&(A.canBeUninvoked()))
						A.unInvoke();
				}
				if(room.isSavable())
				{
					CMLib.database().DBReReadRoomData(room);
					CMLib.database().DBReadContent(room.roomID(),room,true);
				}
				room.startItemRejuv();
				room.setResource(-1);
			}
			finally
			{
				room.toggleMobility(mobile);
			}
		}
	}

	@Override
	public Room findWorldRoomLiberally(final MOB mob, final String cmd, final String srchWhatAERIPMVK, final int timePct, final long maxMillis)
	{
		final List<Room> rooms=findWorldRoomsLiberally(mob,cmd,srchWhatAERIPMVK,null,true,timePct, maxMillis);
		if((rooms!=null)&&(rooms.size()!=0))
			return rooms.get(0);
		return null;
	}

	@Override
	public List<Room> findWorldRoomsLiberally(final MOB mob, final String cmd, final String srchWhatAERIPMVK, final int timePct, final long maxMillis)
	{
		return findWorldRoomsLiberally(mob,cmd,srchWhatAERIPMVK,null,false,timePct,maxMillis);
	}

	@Override
	public Room findAreaRoomLiberally(final MOB mob, final Area A,final String cmd, final String srchWhatAERIPMVK, final int timePct)
	{
		final List<Room> rooms=findWorldRoomsLiberally(mob,cmd,srchWhatAERIPMVK,A,true,timePct,120);
		if((rooms!=null)&&(rooms.size()!=0))
			return rooms.get(0);
		return null;
	}

	@Override
	public List<Room> findAreaRoomsLiberally(final MOB mob, final Area A,final String cmd, final String srchWhatAERIPMVK, final int timePct)
	{
		return findWorldRoomsLiberally(mob,cmd,srchWhatAERIPMVK,A,false,timePct,120);
	}

	protected Room addWorldRoomsLiberally(final List<Room> rooms, final List<? extends Environmental> choicesV)
	{
		if(choicesV==null)
			return null;
		if(rooms!=null)
		{
			for(final Environmental E : choicesV)
				addWorldRoomsLiberally(rooms,roomLocation(E));
			return null;
		}
		else
		{
			Room room=null;
			int tries=0;
			while(((room==null)||(room.roomID().length()==0))&&((++tries)<200))
				room=roomLocation(choicesV.get(CMLib.dice().roll(1,choicesV.size(),-1)));
			return room;
		}
	}

	protected Room addWorldRoomsLiberally(final List<Room> rooms, final Room room)
	{
		if(room==null)
			return null;
		if(rooms!=null)
		{
			if(!rooms.contains(room))
				rooms.add(room);
			return null;
		}
		return room;
	}

	protected Room addWorldRoomsLiberally(final List<Room>rooms, final Area area)
	{
		if((area==null)||(area.isProperlyEmpty()))
			return null;
		return addWorldRoomsLiberally(rooms,area.getRandomProperRoom());
	}

	protected List<Room> returnResponse(final List<Room> rooms, final Room room)
	{
		if(rooms!=null)
			return rooms;
		if(room==null)
			return new Vector<Room>(1);
		return new XVector<Room>(room);
	}

	protected boolean enforceTimeLimit(final long startTime,  final long maxMillis)
	{
		if(maxMillis<=0)
			return false;
		return ((System.currentTimeMillis() - startTime)) > maxMillis;
	}

	protected List<MOB> checkMOBCachedList(final List<MOB> list)
	{
		if (list != null)
		{
			for(final Environmental E : list)
				if(E.amDestroyed())
					return null;
		}
		return list;
	}

	protected List<Item> checkInvCachedList(final List<Item> list)
	{
		if (list != null)
		{
			for(final Item E : list)
				if((E.amDestroyed())||(!(E.owner() instanceof MOB)))
					return null;
		}
		return list;
	}

	protected List<Item> checkRoomItemCachedList(final List<Item> list)
	{
		if (list != null)
		{
			for(final Item E : list)
				if((E.amDestroyed())||(!(E.owner() instanceof Room)))
					return null;
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	public Map<String,List<MOB>> getMOBFinder()
	{
		Map<String,List<MOB>> finder=(Map<String,List<MOB>>)Resources.getResource("SYSTEM_MOB_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,List<MOB>>(10,EXPIRE_5MINS,EXPIRE_10MINS,100);
			Resources.submitResource("SYSTEM_MOB_FINDER_CACHE",finder);
		}
		return finder;
	}
	@SuppressWarnings("unchecked")
	public Map<String,Area> getAreaFinder()
	{
		Map<String,Area> finder=(Map<String,Area>)Resources.getResource("SYSTEM_AREA_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,Area>(50,EXPIRE_30MINS,EXPIRE_1HOUR,100);
			Resources.submitResource("SYSTEM_AREA_FINDER_CACHE",finder);
		}
		return finder;
	}

	@SuppressWarnings("unchecked")
	public Map<String,List<Item>> getRoomItemFinder()
	{
		Map<String,List<Item>> finder=(Map<String,List<Item>>)Resources.getResource("SYSTEM_RITEM_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,List<Item>>(10,EXPIRE_5MINS,EXPIRE_10MINS,100);
			Resources.submitResource("SYSTEM_RITEM_FINDER_CACHE",finder);
		}
		return finder;
	}

	@SuppressWarnings("unchecked")
	public Map<String,List<Item>> getInvItemFinder()
	{
		Map<String,List<Item>> finder=(Map<String,List<Item>>)Resources.getResource("SYSTEM_IITEM_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,List<Item>>(10,EXPIRE_1MIN,EXPIRE_10MINS,100);
			Resources.submitResource("SYSTEM_IITEM_FINDER_CACHE",finder);
		}
		return finder;
	}

	@SuppressWarnings("unchecked")
	public Map<String,List<Environmental>> getStockFinder()
	{
		Map<String,List<Environmental>> finder=(Map<String,List<Environmental>>)Resources.getResource("SYSTEM_STOCK_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,List<Environmental>>(10,EXPIRE_10MINS,EXPIRE_1HOUR,100);
			Resources.submitResource("SYSTEM_STOCK_FINDER_CACHE",finder);
		}
		return finder;
	}

	@SuppressWarnings("unchecked")
	public Map<String,List<Room>> getRoomFinder()
	{
		Map<String,List<Room>> finder=(Map<String,List<Room>>)Resources.getResource("SYSTEM_ROOM_FINDER_CACHE");
		if(finder==null)
		{
			finder=new PrioritizingLimitedMap<String,List<Room>>(20,EXPIRE_20MINS,EXPIRE_1HOUR,100);
			Resources.submitResource("SYSTEM_ROOM_FINDER_CACHE",finder);
		}
		return finder;
	}

	protected List<Room> findWorldRoomsLiberally(final MOB mob,
												 final String cmd,
												 final String srchWhatAERIPMVK,
												 Area area,
												 final boolean returnFirst,
												 final int timePct,
												 final long maxMillis)
	{
		Room room=null;
		// wish this stuff could be cached, even temporarily, however,
		// far too much of the world is dynamic, and far too many searches
		// are looking for dynamic things.  the cached results would be useless
		// as soon as they are put away -- that's why the limited caches time them out!
		final boolean disableCaching= CMProps.getBoolVar(CMProps.Bool.MAPFINDSNOCACHE);

		final Vector<Room> rooms=(returnFirst)?null:new Vector<Room>();
		final Room curRoom=(mob!=null)?mob.location():null;

		boolean searchWeakAreas=false;
		boolean searchStrictAreas=false;
		boolean searchRooms=false;
		boolean searchPlayers=false;
		boolean searchItems=false;
		boolean searchInhabs=false;
		boolean searchInventories=false;
		boolean searchStocks=false;
		final char[] flags = srchWhatAERIPMVK.toUpperCase().toCharArray();
		for (final char flag : flags)
		{
			switch(flag)
			{
			case 'E':
				searchWeakAreas = true;
				break;
			case 'A':
				searchStrictAreas = true;
				break;
			case 'R':
				searchRooms = true;
				break;
			case 'P':
				searchPlayers = true;
				break;
			case 'I':
				searchItems = true;
				break;
			case 'M':
				searchInhabs = true;
				break;
			case 'V':
				searchInventories = true;
				break;
			case 'K':
				searchStocks = true;
				break;
			}
		}
		final long startTime = System.currentTimeMillis();
		if(searchRooms)
		{
			final int dirCode=CMLib.directions().getGoodDirectionCode(cmd);
			if((dirCode>=0)&&(curRoom!=null))
				room=addWorldRoomsLiberally(rooms,curRoom.rawDoors()[dirCode]);
			if(room==null)
				room=addWorldRoomsLiberally(rooms,getRoom(cmd));
			if((room == null) && (curRoom != null) && (curRoom.getArea()!=null))
				room=addWorldRoomsLiberally(rooms,curRoom.getArea().getRoom(cmd));
		}

		if(room==null)
		{
			// first get room ids
			if((cmd.charAt(0)=='#')&&(curRoom!=null)&&(searchRooms))
			{
				room=addWorldRoomsLiberally(rooms,getRoom(curRoom.getArea().Name()+cmd));
				if(room == null)
					room=addWorldRoomsLiberally(rooms,curRoom.getArea().getRoom(curRoom.getArea().Name()+cmd));
			}
			else
			{
				final String srchStr=cmd;

				if(searchPlayers)
				{
					// then look for players
					final MOB M=CMLib.sessions().findPlayerOnline(srchStr,false);
					if(M!=null)
						room=addWorldRoomsLiberally(rooms,M.location());
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// search areas strictly
				if(searchStrictAreas && (room==null) && (area==null))
				{
					area=getArea(srchStr);
					if((area!=null) &&(area.properSize()>0) &&(area.getProperRoomnumbers().roomCountAllAreas()>0))
						room=addWorldRoomsLiberally(rooms,area);
					area=null;
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				final Area A=area;
				final MultiEnumeratorBuilder<Room> roomer = new MultiEnumeratorBuilder<Room>()
				{
					@SuppressWarnings("unchecked")

					@Override
					public MultiEnumeration<Room> getList()
					{
						if(A==null)
							return new MultiEnumeration<Room>(roomsFilled());
						else
							return new MultiEnumeration<Room>(new Enumeration[]{A.getProperMap(),shipsRoomEnumerator(A)});
					}
				};

				// no good, so look for room inhabitants
				if(searchInhabs && room==null)
				{
					final Map<String,List<MOB>> finder=getMOBFinder();
					List<MOB> candidates=null;

					if((mob==null)||(mob.isMonster()))
					{
						candidates=checkMOBCachedList(finder.get(srchStr.toLowerCase()));
						if(returnFirst&&(candidates!=null)&&(candidates.size()>1))
							candidates=new XVector<MOB>(candidates.get(0));
					}
					if(candidates==null)
					{
						candidates=findInhabitants(roomer.getList(), mob, srchStr,returnFirst, timePct);
						if((!disableCaching)&&(!returnFirst)&&((mob==null)||(mob.isMonster())))
							finder.put(srchStr.toLowerCase(), candidates);

					}
					if(candidates.size()>0)
						room=addWorldRoomsLiberally(rooms,candidates);
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// now check room text
				if(searchRooms && room==null)
				{
					final Map<String,List<Room>> finder=getRoomFinder();
					List<Room> candidates=null;
					if((mob==null)||(mob.isMonster()))
					{
						candidates=finder.get(srchStr.toLowerCase());
						if(returnFirst&&(candidates!=null)&&(candidates.size()>1))
							candidates=new XVector<Room>(candidates.get(0));
					}
					if(candidates==null)
					{
						candidates=findRooms(roomer.getList(), mob, srchStr, false,returnFirst, timePct);
						if((!disableCaching)&&(!returnFirst)&&((mob==null)||(mob.isMonster())))
							finder.put(srchStr.toLowerCase(), candidates);
					}
					if(candidates.size()>0)
						room=addWorldRoomsLiberally(rooms,candidates);
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// check floor items
				if(searchItems && room==null)
				{
					final Map<String,List<Item>> finder=getRoomItemFinder();
					List<Item> candidates=null;
					if((mob==null)||(mob.isMonster()))
					{
						candidates=checkRoomItemCachedList(finder.get(srchStr.toLowerCase()));
						if(returnFirst&&(candidates!=null)&&(candidates.size()>1))
							candidates=new XVector<Item>(candidates.get(0));
					}
					if(candidates==null)
					{
						candidates=findRoomItems(roomer.getList(), mob, srchStr, false,returnFirst,timePct);
						if((!disableCaching)&&(!returnFirst)&&((mob==null)||(mob.isMonster())))
							finder.put(srchStr.toLowerCase(), candidates);
					}
					if(candidates.size()>0)
						room=addWorldRoomsLiberally(rooms,candidates);
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// check inventories
				if(searchInventories && room==null)
				{
					final Map<String,List<Item>> finder=getInvItemFinder();
					List<Item> candidates=null;
					if((mob==null)||(mob.isMonster()))
					{
						candidates=checkInvCachedList(finder.get(srchStr.toLowerCase()));
						if(returnFirst&&(candidates!=null)&&(candidates.size()>1))
							candidates=new XVector<Item>(candidates.get(0));
					}
					if(candidates==null)
					{
						candidates=findInventory(roomer.getList(), mob, srchStr, returnFirst,timePct);
						if((!disableCaching)&&(!returnFirst)&&((mob==null)||(mob.isMonster())))
							finder.put(srchStr.toLowerCase(), candidates);
					}
					if(candidates.size()>0)
						room=addWorldRoomsLiberally(rooms,candidates);
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// check stocks
				if(searchStocks && room==null)
				{
					final Map<String,List<Environmental>> finder=getStockFinder();
					List<Environmental> candidates=null;
					if((mob==null)||(mob.isMonster()))
					{
						candidates=finder.get(srchStr.toLowerCase());
						if(returnFirst&&(candidates!=null)&&(candidates.size()>1))
							candidates=new XVector<Environmental>(candidates.get(0));
					}
					if(candidates==null)
					{
						candidates=findShopStock(roomer.getList(), mob, srchStr, returnFirst,false,timePct);
						if((!disableCaching)&&(!returnFirst)&&((mob==null)||(mob.isMonster())))
							finder.put(srchStr.toLowerCase(), candidates);
					}
					if(candidates.size()>0)
						room=addWorldRoomsLiberally(rooms,candidates);
				}
				if(enforceTimeLimit(startTime,maxMillis))
					return returnResponse(rooms,room);

				// search areas weakly
				if(searchWeakAreas && (room==null) && (A==null))
				{
					final Area A2=findArea(srchStr);
					if((A2!=null) &&(A2.properSize()>0) &&(A2.getProperRoomnumbers().roomCountAllAreas()>0))
						room=addWorldRoomsLiberally(rooms,A2);
				}
			}
		}
		final List<Room> responseSet = returnResponse(rooms,room);
		return responseSet;
	}

	@Override
	public boolean isHere(final CMObject E2, final Room here)
	{
		if(E2==null)
			return false;
		else
		if(E2==here)
			return true;
		else
		if((E2 instanceof MOB)
		&&(((MOB)E2).location()==here))
			return true;
		else
		if((E2 instanceof Item)
		&&(((Item)E2).owner()==here))
			return true;
		else
		if((E2 instanceof Item)
		&&(((Item)E2).owner()!=null)
		&&(((Item)E2).owner() instanceof MOB)
		&&(((MOB)((Item)E2).owner()).location()==here))
			return true;
		else
		if(E2 instanceof Exit)
		{
			for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
				if(here.getRawExit(d)==E2)
					return true;
		}
		return false;
	}

	@Override
	public boolean isHere(final CMObject E2, final Area here)
	{
		if(E2==null)
			return false;
		else
		if(E2==here)
			return true;
		else
		if(E2 instanceof Room)
			return ((Room)E2).getArea()==here;
		else
		if(E2 instanceof MOB)
			return isHere(((MOB)E2).location(),here);
		else
		if(E2 instanceof Item)
			return isHere(((Item)E2).owner(),here);
		return false;
	}

	protected PairVector<MOB,String> getAllPlayersHere(final Area area, final boolean includeLocalFollowers)
	{
		final PairVector<MOB,String> playersHere=new PairVector<MOB,String>();
		MOB M=null;
		Room R=null;
		for(final Session S : CMLib.sessions().localOnlineIterable())
		{
			M=S.mob();
			R=(M!=null)?M.location():null;
			if((R!=null)&&(R.getArea()==area)&&(M!=null))
			{
				playersHere.addElement(M,getExtendedRoomID(R));
				if(includeLocalFollowers)
				{
					MOB M2=null;
					final Set<MOB> H=M.getGroupMembers(new HashSet<MOB>());
					for(final Iterator<MOB> i=H.iterator();i.hasNext();)
					{
						M2=i.next();
						if((M2!=M)&&(M2.location()==R))
							playersHere.addElement(M2,getExtendedRoomID(R));
					}
				}
			}
		}
		return playersHere;

	}

	@Override
	public void resetArea(final Area area)
	{
		final Area.State oldFlag=area.getAreaState();
		area.setAreaState(Area.State.FROZEN);
		final PairVector<MOB,String> playersHere=getAllPlayersHere(area,true);
		final PairVector<PrivateProperty, String> propertyHere=new PairVector<PrivateProperty, String>();
		for(int p=0;p<playersHere.size();p++)
		{
			final MOB M=playersHere.elementAt(p).first;
			final Room R=M.location();
			R.delInhabitant(M);
		}
		for(final Enumeration<BoardableShip> b=ships();b.hasMoreElements();)
		{
			final BoardableShip ship=b.nextElement();
			final Room R=roomLocation(ship);
			if((R!=null)
			&&(R.getArea()==area)
			&&(ship instanceof PrivateProperty)
			&&(((PrivateProperty)ship).getOwnerName().length()>0)
			&&(ship instanceof Item))
			{
				R.delItem((Item)ship);
				propertyHere.add((PrivateProperty)ship,CMLib.map().getExtendedRoomID(R));
			}
		}

		for(final Enumeration<Room> r=area.getProperMap();r.hasMoreElements();)
			resetRoom(r.nextElement());
		area.fillInAreaRooms();
		for(int p=0;p<playersHere.size();p++)
		{
			final MOB M=playersHere.elementAt(p).first;
			Room R=getRoom(playersHere.elementAt(p).second);
			if(R==null)
				R=M.getStartRoom();
			if(R==null)
				R=getStartRoom(M);
			if(R!=null)
				R.bringMobHere(M,false);
		}
		for(int p=0;p<propertyHere.size();p++)
		{
			final PrivateProperty P=propertyHere.elementAt(p).first;
			Room R=getRoom(propertyHere.elementAt(p).second);
			if((R==null)||(R.amDestroyed()))
				R=getSafeRoomToMovePropertyTo((R==null)?area.getRandomProperRoom():R,P);
			if(R!=null)
				R.moveItemTo((Item)P);
		}
		CMLib.database().DBReadAreaData(area);
		area.setAreaState(oldFlag);
	}

	@Override
	public boolean hasASky(final Room room)
	{
		if((room==null)
		||(room.domainType()==Room.DOMAIN_OUTDOORS_UNDERWATER)
		||((room.domainType()&Room.INDOORS)>0))
			return false;
		return true;
	}

	@Override
	public void registerWorldObjectDestroyed(Area area, final Room room, final CMObject o)
	{
		if(o instanceof Deity)
			delDeity((Deity)o);

		if((o instanceof BoardableShip)&&(!(o instanceof Area)))
			delShip((BoardableShip)o);

		if(o instanceof PostOffice)
			delPostOffice((PostOffice)o);

		if(o instanceof Librarian)
			delLibrary((Librarian)o);

		if(o instanceof Banker)
			delBank((Banker)o);

		if(o instanceof Auctioneer)
			delAuctionHouse((Auctioneer)o);

		if(o instanceof PhysicalAgent)
		{
			final PhysicalAgent AE=(PhysicalAgent)o;
			if((area == null) && (room!=null))
				area = room.getArea();
			if(area == null)
				area =getStartArea(AE);
			delScriptHost(area, AE);
		}
	}

	@Override
	public void registerWorldObjectLoaded(Area area, Room room, final CMObject o)
	{
		if(o instanceof Deity)
			addDeity((Deity)o);

		if(o instanceof BoardableShip)
			addShip((BoardableShip)o);

		if(o instanceof PostOffice)
			addPostOffice((PostOffice)o);

		if(o instanceof Banker)
			addBank((Banker)o);

		if(o instanceof Librarian)
			addLibrary((Librarian)o);

		if(o instanceof Auctioneer)
			addAuctionHouse((Auctioneer)o);

		if(o instanceof PhysicalAgent)
		{
			final PhysicalAgent AE=(PhysicalAgent)o;
			if(room == null)
				room = getStartRoom(AE);
			if((area == null) && (room!=null))
				area = room.getArea();
			if(area == null)
				area = getStartArea(AE);
			addScriptHost(area, room, AE);
			if(o instanceof MOB)
			{
				for(final Enumeration<Item> i=((MOB)o).items();i.hasMoreElements();)
					addScriptHost(area, room, i.nextElement());
			}
		}
	}

	protected void cleanScriptHosts(final SLinkedList<LocatedPair> hosts, final PhysicalAgent oneToDel, final boolean fullCleaning)
	{
		PhysicalAgent PA;
		for (final LocatedPair W : hosts)
		{
			if(W==null)
				hosts.remove(W);
			else
			{
				PA=W.obj();
				if((PA==null)
				||(PA==oneToDel)
				||(PA.amDestroyed())
				||((fullCleaning)&&(!isAQualifyingScriptHost(PA))))
					hosts.remove(W);
			}
		}
	}

	protected boolean isAQualifyingScriptHost(final PhysicalAgent host)
	{
		if(host==null)
			return false;
		for(final Enumeration<Behavior> e = host.behaviors();e.hasMoreElements();)
		{
			final Behavior B=e.nextElement();
			if((B!=null) && B.isSavable() && (B instanceof ScriptingEngine))
				return true;
		}
		for(final Enumeration<ScriptingEngine> e = host.scripts();e.hasMoreElements();)
		{
			final ScriptingEngine SE=e.nextElement();
			if((SE!=null) && SE.isSavable())
				return true;
		}
		return false;
	}

	@Override
	public int numSpaceObjects()
	{
		return space.count();
	}

	protected boolean isAScriptHost(final Area area, final PhysicalAgent host)
	{
		if(area == null)
			return false;
		return isAScriptHost(scriptHostMap.get(area.Name()), host);
	}

	protected boolean isAScriptHost(final SLinkedList<LocatedPair> hosts, final PhysicalAgent host)
	{
		if((hosts==null)||(host==null)||(hosts.size()==0))
			return false;
		for (final LocatedPair W : hosts)
		{
			if(W.obj()==host)
				return true;
		}
		return false;
	}

	protected final Object getScriptHostSemaphore(final Area area)
	{
		final Object semaphore;
		if(SCRIPT_HOST_SEMAPHORES.containsKey(area.Name()))
			semaphore=SCRIPT_HOST_SEMAPHORES.get(area.Name());
		else
		{
			synchronized(SCRIPT_HOST_SEMAPHORES)
			{
				semaphore=new Object();
				SCRIPT_HOST_SEMAPHORES.put(area.Name(), semaphore);
			}
		}
		return semaphore;
	}

	protected void addScriptHost(final Area area, final Room room, final PhysicalAgent host)
	{
		if((area==null) || (host == null))
			return;
		if(!isAQualifyingScriptHost(host))
			return;
		synchronized(getScriptHostSemaphore(area))
		{
			SLinkedList<LocatedPair> hosts = scriptHostMap.get(area.Name());
			if(hosts == null)
			{
				hosts=new SLinkedList<LocatedPair>();
				scriptHostMap.put(area.Name(), hosts);
			}
			else
			{
				cleanScriptHosts(hosts, null, false);
				if(isAScriptHost(hosts,host))
					return;
			}
			hosts.add(new LocatedPairImpl(room, host));
		}
	}

	protected void delScriptHost(Area area, final PhysicalAgent oneToDel)
	{
		if(oneToDel == null)
			return;
		if(area == null)
		{
			for(final Area A : areasList)
			{
				if(isAScriptHost(A,oneToDel))
				{
					area = A;
					break;
				}
			}
		}
		if(area == null)
			return;
		synchronized(getScriptHostSemaphore(area))
		{
			final SLinkedList<LocatedPair> hosts = scriptHostMap.get(area.Name());
			if(hosts==null)
				return;
			cleanScriptHosts(hosts, oneToDel, false);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Enumeration<LocatedPair> scriptHosts(final Area area)
	{
		final LinkedList<List<LocatedPair>> V = new LinkedList<List<LocatedPair>>();
		if(area == null)
		{
			for(final String areaKey : scriptHostMap.keySet())
				V.add(scriptHostMap.get(areaKey));
		}
		else
		{
			final SLinkedList<LocatedPair> hosts = scriptHostMap.get(area.Name());
			if(hosts==null)
				return EmptyEnumeration.INSTANCE;
			V.add(hosts);
		}
		if(V.size()==0)
			return EmptyEnumeration.INSTANCE;
		final MultiListEnumeration<LocatedPair> me=new MultiListEnumeration<LocatedPair>(V,true);
		return new Enumeration<LocatedPair>()
		{
			@Override
			public boolean hasMoreElements()
			{
				return me.hasMoreElements();
			}

			@Override
			public LocatedPair nextElement()
			{
				final LocatedPair W = me.nextElement();
				final PhysicalAgent E = W.obj();
				if(((E==null) || (E.amDestroyed())) && hasMoreElements())
					return nextElement();
				return W;
			}
		};
	}

	@Override
	public boolean activate()
	{
		if(serviceClient==null)
		{
			name="THMap"+Thread.currentThread().getThreadGroup().getName().charAt(0);
			serviceClient=CMLib.threads().startTickDown(this, Tickable.TICKID_SUPPORT|Tickable.TICKID_SOLITARYMASK, MudHost.TIME_SAVETHREAD_SLEEP, 1);
		}
		return true;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((!CMSecurity.isDisabled(CMSecurity.DisFlag.SAVETHREAD))
		&&(!CMSecurity.isDisabled(CMSecurity.DisFlag.MAPTHREAD))
		&&(tickStatus == Tickable.STATUS_NOT))
		{
			try
			{
				tickStatus=Tickable.STATUS_ALIVE;
				isDebugging=CMSecurity.isDebugging(DbgFlag.MAPTHREAD);
				if(checkDatabase())
					roomMaintSweep();
				setThreadStatus(serviceClient,"saving props");
				Resources.savePropResources();
			}
			finally
			{
				tickStatus=Tickable.STATUS_NOT;
				setThreadStatus(serviceClient,"sleeping");
			}
		}
		return true;
	}

	@Override
	public boolean shutdown()
	{
		final boolean debugMem = CMSecurity.isDebugging(CMSecurity.DbgFlag.SHUTDOWN);
		for(final Enumeration<Area> a=areasList.elements();a.hasMoreElements();)
		{
			try
			{
				final Area A = a.nextElement();
				if(A!=null)
				{
					CMProps.setUpAllLowVar(CMProps.Str.MUDSTATUS,"Shutting down Map area '"+A.Name()+"'...");
					final LinkedList<Room> rooms=new LinkedList<Room>();
					for(final Enumeration<Room> r=A.getProperMap();r.hasMoreElements();)
					{
						try
						{
							final Room R=r.nextElement();
							if(R!=null)
								rooms.add(R);
						}
						catch(final Exception e)
						{
						}
					}
					for(final Iterator<Room> r=rooms.iterator();r.hasNext();)
					{
						try
						{
							final Room R=r.next();
							A.delProperRoom(R);
							R.destroy();
						}
						catch(final Exception e)
						{
						}
					}
				}
				if(debugMem)
				{
					try
					{
						Object obj = new Object();
						final WeakReference<Object> ref = new WeakReference<Object>(obj);
						obj = null;
						System.gc();
						System.runFinalization();
						while(ref.get() != null)
						{
							System.gc();
						}
						Thread.sleep(3000);
					}
					catch (final Exception e)
					{
					}
					final long free=Runtime.getRuntime().freeMemory()/1024;
					final long total=Runtime.getRuntime().totalMemory()/1024;
					if(A!=null)
						Log.debugOut("Memory: CMMap: "+A.Name()+": "+(total-free)+"/"+total);
				}
			}
			catch (final Exception e)
			{
			}
		}
		areasList.clear();
		deitiesList.clear();
		shipList.clear();
		space.clear();
		globalHandlers.clear();
		if(CMLib.threads().isTicking(this, TICKID_SUPPORT|Tickable.TICKID_SOLITARYMASK))
		{
			CMLib.threads().deleteTick(this, TICKID_SUPPORT|Tickable.TICKID_SOLITARYMASK);
			serviceClient=null;
		}
		return true;
	}

	public void roomMaintSweep()
	{
		final boolean corpsesOnly=CMSecurity.isSaveFlag(CMSecurity.SaveFlag.ROOMITEMS);
		final boolean noMobs=CMSecurity.isSaveFlag(CMSecurity.SaveFlag.ROOMMOBS);
		setThreadStatus(serviceClient,"expiration sweep");
		final long currentTime=System.currentTimeMillis();
		final boolean debug=CMSecurity.isDebugging(CMSecurity.DbgFlag.VACUUM);
		final MOB expireM=getFactoryMOB(null);
		try
		{
			final List<Environmental> stuffToGo=new LinkedList<Environmental>();
			final List<Room> roomsToGo=new LinkedList<Room>();
			final CMMsg expireMsg=CMClass.getMsg(expireM,null,null,CMMsg.MSG_EXPIRE,null);
			for(final Enumeration<Room> r=roomsFilled();r.hasMoreElements();)
			{
				final Room R=r.nextElement();
				expireM.setLocation(R);
				expireMsg.setTarget(R);
				if((R.expirationDate()!=0)
				&&(currentTime>R.expirationDate())
				&&(R.okMessage(R,expireMsg)))
					roomsToGo.add(R);
				else
				if(!R.amDestroyed())
				{
					stuffToGo.clear();
					for(int i=0;i<R.numItems();i++)
					{
						final Item I=R.getItem(i);
						if((I!=null)
						&&((!corpsesOnly)||(I instanceof DeadBody))
						&&(I.expirationDate()!=0)
						&&(I.owner()==R)
						&&(currentTime>I.expirationDate()))
							stuffToGo.add(I);
					}
					if(!noMobs)
					{
						for(int i=0;i<R.numInhabitants();i++)
						{
							final MOB M=R.fetchInhabitant(i);
							if((M!=null)
							&&(M.expirationDate()!=0)
							&&(currentTime>M.expirationDate()))
								stuffToGo.add(M);
						}
					}
				}
				if(stuffToGo.size()>0)
				{
					boolean success=true;
					for(final Environmental E : stuffToGo)
					{
						setThreadStatus(serviceClient,"expiring "+E.Name());
						expireMsg.setTarget(E);
						if(R.okMessage(expireM,expireMsg))
							R.sendOthers(expireM,expireMsg);
						else
							success=false;
						if(debug)
							Log.sysOut("UTILITHREAD","Expired "+E.Name()+" in "+getExtendedRoomID(R)+": "+success);
					}
					stuffToGo.clear();
				}
			}
			for(final Room R : roomsToGo)
			{
				expireM.setLocation(R);
				expireMsg.setTarget(R);
				setThreadStatus(serviceClient,"expirating room "+getExtendedRoomID(R));
				if(debug)
				{
					String roomID=getExtendedRoomID(R);
					if(roomID.length()==0)
						roomID="(unassigned grid room, probably in the air)";
					if(debug)
						Log.sysOut("UTILITHREAD","Expired "+roomID+".");
				}
				R.sendOthers(expireM,expireMsg);
			}
		}
		catch(final java.util.NoSuchElementException e)
		{
		}
		setThreadStatus(serviceClient,"title sweeping");
		final LegalLibrary law=CMLib.law();
		final Set<String> playerList=new TreeSet<String>();
		try
		{
			final Set<LandTitle> titlesDone = new HashSet<LandTitle>();
			for(final Enumeration<Area> a=areas();a.hasMoreElements();)
			{
				final Area A=a.nextElement();
				if(A.numEffects()>0)
				{
					final LandTitle T=law.getLandTitle(A);
					if((T!=null)
					&&(!titlesDone.contains(T)))
					{
						T.updateLot(playerList);
						titlesDone.add(T);
					}
				}
			}
			for(final Enumeration<Room> r=rooms();r.hasMoreElements();)
			{
				final Room R=r.nextElement();
				// roomid > 0? these are unfilled...
				if(R.numEffects()>0)
				{
					for(final Enumeration<Ability> a=R.effects();a.hasMoreElements();)
					{
						final Ability A=a.nextElement();
						if(A instanceof LandTitle)
						{
							final LandTitle T=(LandTitle)A;
							if(!titlesDone.contains(T))
							{
								T.updateLot(playerList);
								titlesDone.add(T);
							}
						}
					}
				}
			}
		}
		catch(final NoSuchElementException nse)
		{
		}

		setThreadStatus(serviceClient,"cleaning scripts");
		for(final String areaKey : scriptHostMap.keySet())
			cleanScriptHosts(scriptHostMap.get(areaKey), null, true);

		final long lastDateTime=System.currentTimeMillis()-(5*TimeManager.MILI_MINUTE);
		setThreadStatus(serviceClient,"checking");
		try
		{
			for(final Enumeration<Room> r=roomsFilled();r.hasMoreElements();)
			{
				final Room R=r.nextElement();
				for(int m=0;m<R.numInhabitants();m++)
				{
					final MOB mob=R.fetchInhabitant(m);
					if(mob == null)
						continue;
					if(mob.amDestroyed())
					{
						R.delInhabitant(mob);
						continue;
					}
					if((mob.lastTickedDateTime()>0)
					&&(mob.lastTickedDateTime()<lastDateTime))
					{
						final boolean ticked=CMLib.threads().isTicking(mob,Tickable.TICKID_MOB);
						final boolean isDead=mob.amDead();
						final Room startR=mob.getStartRoom();
						final String wasFrom=(startR!=null)?startR.roomID():"NULL";
						if(!ticked)
						{
							if(!mob.isPlayer())
							{
								if(ticked)
								{
									// we have a dead group.. let the group handler deal with it.
									Log.errOut(serviceClient.getName(),mob.name()+" in room "+CMLib.map().getDescriptiveExtendedRoomID(R)
											+" unticked in dead group (Home="+wasFrom+") since: "+CMLib.time().date2String(mob.lastTickedDateTime())+".");
									continue;
								}
								else
								{
									Log.errOut(serviceClient.getName(),mob.name()+" in room "+CMLib.map().getDescriptiveExtendedRoomID(R)
											+" unticked (is ticking="+(ticked)+", dead="+isDead+", Home="+wasFrom+") since: "+CMLib.time().date2String(mob.lastTickedDateTime())+"."+(ticked?"":"  This mob has been destroyed. May he rest in peace."));
									mob.destroy();
								}
							}
							else
							{
								Log.errOut(serviceClient.getName(),"Player "+mob.name()+" in room "+CMLib.map().getDescriptiveExtendedRoomID(R)
										+" unticked (is ticking="+(ticked)+", dead="+isDead+", Home="+wasFrom+") since: "+CMLib.time().date2String(mob.lastTickedDateTime())+"."+(ticked?"":"  This mob has been put aside."));
							}
							R.delInhabitant(mob);//keeps it from happening again.
							setThreadStatus(serviceClient,"checking");
						}
					}
				}
			}
		}
		catch(final java.util.NoSuchElementException e)
		{
		}
		finally
		{
			if(expireM!=null)
				expireM.destroy();
		}
	}

	protected final static char[] cmfsFilenameifyChars=new char[]{'/','\\',' '};

	protected String cmfsFilenameify(final String str)
	{
		return CMStrings.replaceAllofAny(str, cmfsFilenameifyChars, '_').toLowerCase().trim();
	}

	// this is a beautiful idea, but im scared of the memory of all the final refs
	protected void addMapStatFiles(final List<CMFile.CMVFSFile> rootFiles, final Room R, final Environmental E, final CMFile.CMVFSDir root)
	{
		rootFiles.add(new CMFile.CMVFSDir(root,root.getPath()+"stats/")
		{
			@Override
			protected CMFile.CMVFSFile[] getFiles()
			{
				final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
				final String[] stats=E.getStatCodes();
				final String oldName=E.Name();
				for (final String statName : stats)
				{
					final String statValue=E.getStat(statName);
					myFiles.add(new CMFile.CMVFSFile(this.getPath()+statName,256,System.currentTimeMillis(),"SYS")
					{
						@Override
						public int getMaskBits(final MOB accessor)
						{
							if(accessor==null)
								return this.mask;
							if((E instanceof Area)&&(CMSecurity.isAllowed(accessor,((Area)E).getRandomProperRoom(),CMSecurity.SecFlag.CMDAREAS)))
								return this.mask;
							else
							if(CMSecurity.isAllowed(accessor,R,CMSecurity.SecFlag.CMDROOMS))
								return this.mask;
							else
							if((E instanceof MOB) && CMSecurity.isAllowed(accessor,R,CMSecurity.SecFlag.CMDMOBS))
								return this.mask;
							else
							if((E instanceof Item) && CMSecurity.isAllowed(accessor,R,CMSecurity.SecFlag.CMDITEMS))
								return this.mask;
							return this.mask|48;
						}

						@Override
						public Object readData()
						{
							return statValue;
						}

						@Override
						public void saveData(final String filename, final int vfsBits, final String author, final Object O)
						{
							E.setStat(statName, O.toString());
							if(E instanceof Area)
								CMLib.database().DBUpdateArea(oldName, (Area)E);
							else
							if(E instanceof Room)
								CMLib.database().DBUpdateRoom((Room)E);
							else
							if(E instanceof MOB)
								CMLib.database().DBUpdateMOB(R.roomID(), (MOB)E);
							else
							if(E instanceof Item)
								CMLib.database().DBUpdateItem(R.roomID(), (Item)E);
						}
					});
				}
				Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
				return myFiles.toArray(new CMFile.CMVFSFile[0]);
			}
		});
	}

	@Override
	public CMFile.CMVFSDir getMapRoot(final CMFile.CMVFSDir root)
	{
		return new CMFile.CMVFSDir(root,root.getPath()+"map/")
		{
			@Override
			protected CMFile.CMVFSFile[] getFiles()
			{
				final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>(numAreas());
				for(final Enumeration<Area> a=CMLib.map().areas();a.hasMoreElements();)
				{
					final Area A=a.nextElement();
					myFiles.add(new CMFile.CMVFSFile(this.getPath()+cmfsFilenameify(A.Name())+".cmare",48,System.currentTimeMillis(),"SYS")
					{
						@Override
						public Object readData()
						{
							return CMLib.coffeeMaker().getAreaXML(A, null, null, null, true);
						}
					});
					myFiles.add(new CMFile.CMVFSDir(this,this.getPath()+cmfsFilenameify(A.Name())+"/")
					{
						@Override
						protected CMFile.CMVFSFile[] getFiles()
						{
							final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
							for(final Enumeration<Room> r=A.getFilledProperMap();r.hasMoreElements();)
							{
								final Room R=r.nextElement();
								if(R.roomID().length()>0)
								{
									String roomID=R.roomID();
									if(roomID.startsWith(A.Name()+"#"))
										roomID=roomID.substring(A.Name().length()+1);
									myFiles.add(new CMFile.CMVFSFile(this.getPath()+cmfsFilenameify(R.roomID())+".cmare",48,System.currentTimeMillis(),"SYS")
									{
										@Override
										public Object readData()
										{
											return CMLib.coffeeMaker().getRoomXML(R, null, null, true);
										}
									});
									myFiles.add(new CMFile.CMVFSDir(this,this.getPath()+cmfsFilenameify(roomID).toLowerCase()+"/")
									{
										@Override
										protected CMFile.CMVFSFile[] getFiles()
										{
											final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
											myFiles.add(new CMFile.CMVFSFile(this.getPath()+"items.cmare",48,System.currentTimeMillis(),"SYS")
											{
												@Override
												public Object readData()
												{
													return CMLib.coffeeMaker().getRoomItems(R, new TreeMap<String,List<Item>>(), null, null);
												}
											});
											myFiles.add(new CMFile.CMVFSFile(this.path+"mobs.cmare",48,System.currentTimeMillis(),"SYS")
											{
												@Override
												public Object readData()
												{
													return CMLib.coffeeMaker().getRoomMobs(R, null, null, new TreeMap<String,List<MOB>>());
												}
											});
											myFiles.add(new CMFile.CMVFSDir(this,this.path+"mobs/")
											{
												@Override
												protected CMFile.CMVFSFile[] getFiles()
												{
													final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
													final Room R2=CMLib.coffeeMaker().makeNewRoomContent(R, false);
													if(R2!=null)
													{
														for(int i=0;i<R2.numInhabitants();i++)
														{
															final MOB M=R2.fetchInhabitant(i);
															myFiles.add(new CMFile.CMVFSFile(this.path+cmfsFilenameify(R2.getContextName(M))+".cmare",48,System.currentTimeMillis(),"SYS")
															{
																@Override
																public Object readData()
																{
																	return CMLib.coffeeMaker().getMobXML(M);
																}
															});
															myFiles.add(new CMFile.CMVFSDir(this,this.path+cmfsFilenameify(R2.getContextName(M))+"/")
															{
																@Override
																protected CMFile.CMVFSFile[] getFiles()
																{
																	final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
																	addMapStatFiles(myFiles,R,M,this);
																	Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
																	return myFiles.toArray(new CMFile.CMVFSFile[0]);
																}
															});
														}
														Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
													}
													return myFiles.toArray(new CMFile.CMVFSFile[0]);
												}
											});
											myFiles.add(new CMFile.CMVFSDir(this,this.path+"items/")
											{
												@Override
												protected CMFile.CMVFSFile[] getFiles()
												{
													final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
													final Room R2=CMLib.coffeeMaker().makeNewRoomContent(R, false);
													if(R2 != null)
													{
														for(int i=0;i<R2.numItems();i++)
														{
															final Item I=R2.getItem(i);
															myFiles.add(new CMFile.CMVFSFile(this.path+cmfsFilenameify(R2.getContextName(I))+".cmare",48,System.currentTimeMillis(),"SYS")
															{
																@Override
																public Object readData()
																{
																	return CMLib.coffeeMaker().getItemXML(I);
																}
															});
															myFiles.add(new CMFile.CMVFSDir(this,this.path+cmfsFilenameify(R2.getContextName(I))+"/")
															{
																@Override
																protected CMFile.CMVFSFile[] getFiles()
																{
																	final List<CMFile.CMVFSFile> myFiles=new Vector<CMFile.CMVFSFile>();
																	addMapStatFiles(myFiles,R,I,this);
																	Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
																	return myFiles.toArray(new CMFile.CMVFSFile[0]);
																}
															});
														}
														Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
														return myFiles.toArray(new CMFile.CMVFSFile[0]);
													}
													return new CMFile.CMVFSFile[0];
												}
											});
											addMapStatFiles(myFiles,R,R,this);
											Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
											return myFiles.toArray(new CMFile.CMVFSFile[0]);
										}
									});
								}
							}
							addMapStatFiles(myFiles,null,A,this);
							Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
							return myFiles.toArray(new CMFile.CMVFSFile[0]);
						}
					});
				}
				Collections.sort(myFiles,CMFile.CMVFSDir.fcomparator);
				return myFiles.toArray(new CMFile.CMVFSFile[0]);
			}
		};
	}

	public static BigDecimal bigSqrt(final BigDecimal A)
	{
		if(A.doubleValue()<0)
			return ZERO;
		final int SCALE=0;
		BigDecimal x0 = new BigDecimal("0");
		BigDecimal x1 = new BigDecimal(Math.sqrt(A.doubleValue()));
		int times=0;
		while ((!x0.equals(x1))&&(++times<20))
		{
			x0 = x1;
			x1 = A.divide(x0, SCALE, RoundingMode.UP);
			x1 = x1.add(x0);
			x1 = x1.divide(TWO, SCALE, RoundingMode.UP);
		}
		return x1;
	}

	protected final double getDirDiffSum(final double[] d1, final double d2[])
	{
		final double sum1=d1[0]>d2[0]?d1[0]-d2[0]:d2[0]-d1[0];
		final double sum2=d1[0]>d2[0]?(PI_TIMES_2-d1[0]+d2[0]):(PI_TIMES_2-d2[0]+d1[0]);
		final double sum3=d1[1]>d2[1]?d1[1]-d2[1]:d2[1]-d1[1];
		return sum1>sum2?(sum2+sum3):(sum1+sum3);
	}

	@Override
	public double getMinDistanceFrom(final long[] prevPos, final double speed, final double[] dir, final long[] curPosition, final double[] directionTo, final long[] objPos)
	{
		final BigDecimal currentDistance=getBigDistanceFrom(curPosition, objPos);
		if(Arrays.equals(prevPos, curPosition))
			return currentDistance.doubleValue();
		final BigDecimal prevDistance=getBigDistanceFrom(prevPos, objPos);
		final BigDecimal baseDistance=BigDecimal.valueOf(speed);
		if(baseDistance.compareTo(currentDistance.add(prevDistance))>=0)
		{
			//Log.debugOut("0:prevDistance="+prevDistance.longValue()+", baseDistance="+baseDistance.longValue()+", currentDistance="+currentDistance.longValue());
			return 0;
		}
		if(prevDistance.subtract(baseDistance).equals(currentDistance)
		||currentDistance.subtract(baseDistance).equals(prevDistance))
		{
			//Log.debugOut("1:prevDistance="+prevDistance.longValue()+", baseDistance="+baseDistance.longValue()+", currentDistance="+currentDistance.longValue());
			return Math.min(prevDistance.doubleValue(), currentDistance.doubleValue());
		}
		//Log.debugOut("2:prevDistance="+prevDistance.longValue()+", baseDistance="+baseDistance.longValue()+", currentDistance="+currentDistance.longValue());
		final double[] travelDir = dir;
		final double[] prevDirToObject = getDirection(prevPos, objPos);
		final double diDelta=getDirDiffSum(travelDir,prevDirToObject);
		if(diDelta<ZERO_ALMOST)
		{
			final double[] currDirToObject = directionTo;
			final double fiDelta=getDirDiffSum(currDirToObject,prevDirToObject);
			if(fiDelta>ZERO_ALMOST)
				return 0;
			if(prevDistance.compareTo(currentDistance)>0)
				return currentDistance.doubleValue();
			else
				return prevDistance.doubleValue();
		}

		final BigDecimal semiPerimeter=currentDistance.add(prevDistance).add(baseDistance).divide(TWO, RoundingMode.HALF_UP);
		final BigDecimal partOfTriangle=semiPerimeter.multiply(semiPerimeter.subtract(currentDistance))
													.multiply(semiPerimeter.subtract(baseDistance))
													.multiply(semiPerimeter.subtract(prevDistance));

		final BigDecimal areaOfTriangle=bigSqrt(partOfTriangle);
		//Log.debugOut("3:semiPerimeter="+semiPerimeter.longValue()+", areaOfTriangle="+areaOfTriangle.doubleValue());
		if(areaOfTriangle.doubleValue()==0.0)
		{
			//Log.debugOut("3.5:semiPerimeter="+semiPerimeter.longValue()+", areaOfTriangle="+areaOfTriangle.doubleValue());
			if (semiPerimeter.subtract(baseDistance).abs().doubleValue() <= 1)
				return 0;
			else
				return Math.min(prevDistance.doubleValue(), currentDistance.doubleValue());
		}
		//Log.debugOut("4:getMinDistanceFrom="+TWO.multiply(areaOfTriangle).divide(baseDistance, RoundingMode.HALF_UP).doubleValue());
		if((baseDistance.multiply(ONE_THOUSAND).compareTo(currentDistance)<0)
		&&(baseDistance.multiply(ONE_THOUSAND).compareTo(prevDistance)<0))
			return Math.min(prevDistance.doubleValue(), currentDistance.doubleValue());
		return TWO.multiply(areaOfTriangle).divide(baseDistance, RoundingMode.HALF_UP).doubleValue();
	}

	@Override
	public double getMinDistanceFrom(final long[] prevPos, final long[] curPosition, final long[] objPos)
	{

		final double speed=this.getDistanceFrom(prevPos, curPosition);
		final double[] dir=getDirection(prevPos, curPosition);
		final double[] dirTo=getDirection(curPosition, objPos);
		return getMinDistanceFrom(prevPos, speed, dir, curPosition, dirTo, objPos);
	}
}
