package com.planet_ink.coffee_mud.Items.BasicTech;
import com.planet_ink.coffee_mud.Items.Basic.StdBoardable;
import com.planet_ink.coffee_mud.Items.Basic.StdPortal;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor.Expire;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor.Move;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMSecurity.DbgFlag;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.Session.InputCallback;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.ShipDirComponent.ShipDir;
import com.planet_ink.coffee_mud.Items.interfaces.Technical.TechCommand;
import com.planet_ink.coffee_mud.Items.interfaces.Technical.TechType;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

import com.planet_ink.coffee_mud.Libraries.interfaces.*;

/*
   Copyright 2013-2021 Bo Zimmerman

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
public class GenSpaceShip extends StdBoardable implements Electronics, SpaceShip
{
	@Override
	public String ID()
	{
		return "GenSpaceShip";
	}

	protected Manufacturer	cachedManufact  = null;
	protected String	 	manufacturer	= "RANDOM";
	public long[]   		coordinates 	= new long[3];
	public double[] 		direction   	= new double[2];
	public double			roll			= 0.0;
	public double 			speed			= 0;
	protected SpaceObject	spaceTarget 	= null;
	protected double[]		facing			= new double[2];
	protected Set<ShipFlag>	shipFlags		= new SHashSet<ShipFlag>();

	protected volatile double				speedTick	= 0;
	protected volatile List<TechComponent>	sensors		= null;

	public GenSpaceShip()
	{
		super();
		setName("the space ship [NEWNAME]");
		setDisplayText("the space ship [NEWNAME] is here.");
		setMaterial(RawMaterial.RESOURCE_STEEL);
		this.doorName="hatch";
	}

	@Override
	public boolean isGeneric()
	{
		return true;
	}

	@Override
	public void recoverPhyStats()
	{
		super.recoverPhyStats();
	}

	@Override
	protected String getAreaClassType()
	{
		return "StdSpaceShip";
	}

	protected synchronized List<TechComponent> getShipSensors()
	{
		if(sensors == null)
		{
			sensors=new Vector<TechComponent>(1);
			final String circuitKey=getElectronicsKey();
			if(circuitKey.length()>0)
			{
				final List<Electronics> components=CMLib.tech().getMakeRegisteredElectronics(circuitKey);
				for(final Electronics E : components)
				{
					if((E instanceof TechComponent)
					&&(E.getTechType()==TechType.SHIP_SENSOR))
						sensors.add((TechComponent)E);
				}
			}
		}
		return sensors;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(tickID==Tickable.TICKID_AREA) // ticking from the area
		{
			this.speedTick = 0.0;
		}
		return true;
	}

	@Override
	protected Room createFirstRoom()
	{
		final Room R=CMClass.getLocale("MetalRoom");
		R.setDisplayText(L("The Cockpit"));
		return R;
	}

	@Override
	public Area getShipArea()
	{
		if((!destroyed)&&(area==null))
		{
			final Area area=super.getShipArea();
			if(area != null)
				area.setTheme(Area.THEME_TECHNOLOGY);
			return area;
		}
		return super.getShipArea();
	}

	@Override
	public void dockHere(final Room R)
	{
		if((CMSecurity.isDebugging(DbgFlag.SPACESHIP))&&(getIsDocked()==null))
			Log.debugOut("SpaceShip "+name()+" is docking at '"+R.displayText()+"' ("+R.roomID()+")");
		if(R instanceof LocationRoom)
			setCoords(CMLib.map().moveSpaceObject(((LocationRoom)R).coordinates(), ((LocationRoom)R).getDirectionFromCore(), radius()));
		CMLib.map().delObjectInSpace(getShipSpaceObject());
		setSpeed(0);
		super.dockHere(R);
	}

	@Override
	public Room unDock(final boolean moveToOutside)
	{
		if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
			Log.debugOut("SpaceShip "+name()+" is undocking"+(moveToOutside?" into space":""));
		final Room R=getIsDocked();
		final Room exitRoom = super.unDock(moveToOutside);
		if(R instanceof LocationRoom)
		{
			setDirection(Arrays.copyOf(((LocationRoom)R).getDirectionFromCore(),2));
			direction()[0]+=CMLib.dice().plusOrMinus(Math.PI/10.0);
			if(direction[0] > 2*Math.PI)
				direction[0] -= 2*Math.PI;
			if(direction()[0]<0)
				direction()[0]+=(Math.PI*2);
			direction()[1]+=CMath.abs(CMLib.dice().plusOrMinus(Math.PI/10.0));
			if(direction[1] > Math.PI)
				direction[1] -= Math.PI;
			setFacing(Arrays.copyOf(direction(),2));
		}
		if(moveToOutside)
		{
			final SpaceObject o = getShipSpaceObject();
			final SpaceObject planetO = CMLib.map().getSpaceObject(R, true);
			final long[] newCoordinates = CMLib.map().moveSpaceObject(((LocationRoom)R).coordinates(), direction(), radius()+radius());
			if((o != null)&&(R instanceof LocationRoom))
			{
				CMLib.map().addObjectToSpace(o,newCoordinates);
				final double gravity = CMLib.tech().getGravityForce(o, planetO);
				setShipFlag(SpaceShip.ShipFlag.IN_THE_AIR,(gravity > 0.0));
			}
		}
		return exitRoom;
	}

	protected String getElectronicsKey()
	{
		final Area area=this.getShipArea();
		if(area instanceof BoardableShip)
		{
			String registryNum=area.getBlurbFlag("REGISTRY");
			if(registryNum==null)
				registryNum="";
			return area.Name()+registryNum;
		}
		return "";
	}

	@Override
	public void renameShip(final String newName)
	{
		final Area area=this.getShipArea();
		if(area instanceof BoardableShip)
		{
			final String registryNum=getElectronicsKey();
			super.renameShip(newName);
			CMLib.tech().unregisterElectronics(null, registryNum);
			final String newRegistryNum=Double.toString(Math.random());
			area.addBlurbFlag("REGISTRY Registry#"+newRegistryNum.substring(newRegistryNum.indexOf('.')+1));
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(msg.amITarget(this))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_DAMAGE:
			{
				final double myMass=getMass();
				final double hardness = RawMaterial.CODES.HARDNESS(material()) * SpaceObject.Distance.Kilometer.dm;
				msg.setValue((int)Math.round((usesRemaining() * (msg.value() / myMass)) / hardness));
				if(!okAreaMessage(msg,false))
					return false;
				return true;
			}
			}
		}
		return super.okMessage(myHost, msg);
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost,msg);

		if(CMLib.map().areaLocation(msg.source())!=getShipArea())
		{
			for(final TechComponent sensor : getShipSensors())
				sensor.executeMsg(myHost, msg);
		}

		if(msg.amITarget(this))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_ACTIVATE:
				if((CMath.bset(msg.targetMajor(), CMMsg.MASK_CNTRLMSG))&&(msg.targetMessage()!=null))
				{
					final String[] parts=msg.targetMessage().split(" ");
					final TechCommand command=TechCommand.findCommand(parts);
					if(command!=null)
					{
						final Object[] parms=command.confirmAndTranslate(parts);
						if(parms!=null)
						{
							if(command==Technical.TechCommand.ACCELERATION)
							{
								final ShipDirComponent.ShipDir dir=(ShipDirComponent.ShipDir)parms[0];
								final double amount=((Double)parms[1]).doubleValue();
								final boolean isConst = ((Boolean)parms[2]).booleanValue();
								double finalAcceleration = 0;
								Room dockR = getIsDocked();
								if(amount != 0)
								{
									switch(dir)
									{
									case STARBOARD:
										if(dockR==null)
										{
											finalAcceleration = -CMath.mul(amount,0.017);
											facing[0] += finalAcceleration;
											if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
												Log.debugOut("SpaceShip "+name()+" turns "+dir.toString()+" "+Math.toDegrees(finalAcceleration)+" to "+facing[0]);
										}
										break;
									case PORT:
										if(dockR==null)
										{
											finalAcceleration = CMath.mul(amount,0.017);
											facing[0] += finalAcceleration;
											if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
												Log.debugOut("SpaceShip "+name()+" turns "+dir.toString()+" "+Math.toDegrees(finalAcceleration)+" to "+facing[0]);
										}
										break;
									case DORSEL:
										if(dockR==null)
										{
											finalAcceleration = -CMath.mul(amount,0.017);
											facing[1] += finalAcceleration;
											if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
												Log.debugOut("SpaceShip "+name()+" turns "+dir.toString()+" "+Math.toDegrees(finalAcceleration)+" to "+facing[1]);
										}
										break;
									case VENTRAL:
										if(dockR==null)
										{
											finalAcceleration = CMath.mul(amount,0.017);
											facing[1] += finalAcceleration;
											if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
												Log.debugOut("SpaceShip "+name()+" turns "+dir.toString()+" "+Math.toDegrees(finalAcceleration)+" to "+facing[1]);
										}
										break;
									case FORWARD:
									case AFT: // breaking thrusters
									{
										if(dockR!=null)
										{
											if(dir == ShipDir.FORWARD)
											{
												if(amount > SpaceObject.ACCELERATION_G)
												{
													unDock(true);
													dockR=null;
												}
											}
										}
										// this will move it, but will also update speed and direction -- all good!
										final double inAirFactor=(shipFlags.contains(ShipFlag.IN_THE_AIR))?(1.0-getOMLCoeff()):1.0;
										//TODO: calculate inertia gforce damage here, and send the message
										//^^ this should be LIKE acceleration, except it can be modified by antigrav stuff
										if(!isConst)
										{
											// a non-constant thruster means the ship attains speed in one burst,
											// and slows in one burst as well.  It is therefore right and good
											// to eliminate all speed, do a complicated gforce calc, and re-speed
											this.setSpeed(0);
										}
										//force/mass is the Gs felt by the occupants.. not force-mass
										finalAcceleration = amount*inAirFactor;
										if((dockR==null) && ((finalAcceleration-this.speedTick) > 0))
										{
											final double prevSpeed = speed();
											final double[] moveDir = (dir == ShipDir.FORWARD) ? facing() : CMLib.map().getOppositeDir(facing());
											CMLib.map().moveSpaceObject(this,moveDir,finalAcceleration-this.speedTick); // have to do this to know new speed
											if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
												Log.debugOut("SpaceShip "+name()+" accelerates "+dir.toString()+" " +(finalAcceleration-this.speedTick));
											this.speedTick += (finalAcceleration-this.speedTick);
											if((speed() < prevSpeed) && (this.speed() < 0.5)) // enough slowing down!
												setSpeed(0.0);
										}
										break;
									}
									}
									final String code=Technical.TechCommand.ACCELERATED.makeCommand(dir,Double.valueOf(finalAcceleration));
									final MOB mob=CMClass.getFactoryMOB();
									try
									{
										final CMMsg msg2=CMClass.getMsg(mob, this, this, CMMsg.NO_EFFECT, null, CMMsg.MSG_ACTIVATE|CMMsg.MASK_CNTRLMSG, code, CMMsg.NO_EFFECT,null);
										this.sendComputerMessage(mob, msg2);
									}
									finally
									{
										mob.destroy();
									}
									facing[0]=Math.abs(facing[0]%(2*Math.PI));
									facing[1]=Math.abs(facing[1]);
									if(facing[1] > Math.PI)
										facing[1] -= Math.PI;
								}
							}
						}
					}
				}
				break;
			case CMMsg.TYP_DAMAGE: // kinetic damage taken to the outside of the ship
			{
				final long myMass=getMass();
				if((msg.value() > 0)&&(myMass>0))
				{
					//TODO: apply non-collision damage to internal systems instead of hull
					//  only a portions is applied to the hull.
					int hullDamage=0;
					final int baseDamage = msg.value();
					switch(msg.sourceMinor())
					{
					case CMMsg.TYP_COLLISION:
						hullDamage = msg.value();
						break;
					case CMMsg.TYP_ELECTRIC:
					case CMMsg.TYP_ACID:
					case CMMsg.TYP_COLD:
					case CMMsg.TYP_FIRE:
					case CMMsg.TYP_GAS:
					case CMMsg.TYP_LASER:
					case CMMsg.TYP_PARALYZE:
					case CMMsg.TYP_POISON:
					case CMMsg.TYP_SONIC:
					case CMMsg.TYP_UNDEAD:
					case CMMsg.TYP_WATER:
						if(CMLib.dice().getRandomizer().nextDouble() + CMath.div(baseDamage,100) > this.getFinalManufacturer().getReliabilityPct())
							hullDamage=1;
						if(hullDamage < usesRemaining())
						{
							final List<Electronics> list = CMLib.tech().getMakeRegisteredElectronics(CMLib.tech().getElectronicsKey(getShipArea()));
							for(final Iterator<Electronics> i=list.iterator();i.hasNext();)
							{
								final Electronics E=i.next();
								if((E.amDestroyed())
								||(((E.subjectToWearAndTear())&&(E.usesRemaining()>0)))
								||(E instanceof ElecPanel)
								||(E instanceof Software)
								||((E instanceof TechComponent) && (!((TechComponent)E).isInstalled()))
								||((!(E instanceof TechComponent)) && (!E.activated())))
									i.remove();
							}
							if(list.size()>0)
							{
								final Electronics damagedE = list.get(CMLib.dice().roll(1, list.size(), -1));
								final Room R=CMLib.map().roomLocation(damagedE);
								final CMMsg msg2=(CMMsg)msg.copyOf();
								if((R!=null)&&(R.okMessage(msg.source(), msg2)))
									R.send(msg.source(), msg2);
							}
						}
						break;
					}
					if(hullDamage >= usesRemaining())
					{
						if(baseDamage>0)
							msg.setOthersMessage(L("For a split second, you hear a loud deafening crash and feel an incredible jolt."));
						else
							msg.setOthersMessage(L("You hear a loud deafening crash and feel a massive jolt."));
						sendAreaMessage(msg,false);
						if(hullDamage>0)
							destroyThisShip();
					}
					else
					{
						if(hullDamage>0)
							setUsesRemaining(usesRemaining() - hullDamage);
						if(baseDamage > 75)
							msg.setOthersMessage(L("You hear a booming crash and feel a crushing jolt."));
						else
						if(baseDamage > 50)
							msg.setOthersMessage(L("You hear a loud crash and feel a hard jolt."));
						else
						if(baseDamage > 25)
							msg.setOthersMessage(L("You hear a noise and feel a small jolt."));
						else
							msg.setOthersMessage(L("You hear a bump and feel a short rattle."));
						sendAreaMessage(msg,false);
					}
				}
				break;
			}
			case CMMsg.TYP_COLLISION:
			{
				final MOB mob=msg.source();
				final double previousSpeed = speed();
				if((msg.tool() instanceof SpaceObject) // we hit something very very big
				&&(((SpaceObject)msg.tool()).getMass() >= (100 * SpaceObject.Distance.Kilometer.dm)))
				{
					if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
						Log.debugOut("SpaceShip "+name()+" just hit "+msg.tool().Name()+"!!!");
					stopThisShip(mob);
				}

				final long myMass=getMass();
				if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
					Log.debugOut("SpaceShip "+name()+" just collided with "+msg.tool().Name()+" of mass "+myMass+" at speed "+previousSpeed);

				// this only works because Areas don't move.
				// the only way to hit one is to be moving towards it.
				if((previousSpeed > (SpaceObject.ACCELERATION_DAMAGED))
				&&(msg.tool() instanceof SpaceObject))
				{
					final SpaceObject O=(SpaceObject)msg.tool();
					// This is technically wrong. Imagine taping a huge object from behind because you
					// are going just a tiny bit faster, even though you are both going very fast.
					// However, the odds of that happening are nothing.  Forget it.
					double absorbedDamage;
					if(O instanceof ShipWarComponent)
						absorbedDamage = ((ShipWarComponent)O).phyStats().damage();
					else
						absorbedDamage = Math.floor((previousSpeed * myMass) + (O.speed() * O.getMass()));
					if(absorbedDamage > Integer.MAX_VALUE / 10)
						absorbedDamage = Integer.MAX_VALUE / 10;

					// the item should modify the source message type, otherwise, all the damage is absorbed
					// by the hull as collision damage.  Damage may be 0 here, and the weapon may generate more,
					// or convert the collision damage into some other kind.
					final CMMsg sMsg=(CMMsg)msg.copyOf();
					sMsg.setTargetCode(CMMsg.MSG_DAMAGE);
					sMsg.setValue((int)Math.round(absorbedDamage));
					if(O.okMessage(O, sMsg)  && okMessage(this,sMsg))
					{
						O.executeMsg(O, sMsg);
						if(sMsg.value() > 0)
							executeMsg(this,sMsg);
					}
				}

				if((!amDestroyed()) && (msg.tool() instanceof Area))
				{
					final List<LocationRoom> landingPoints=CMLib.map().getLandingPoints(this, msg.tool());
					final LocationRoom LR = landingPoints.size()==0 ? null : landingPoints.get(0);
					stopThisShip(mob);
					if(LR!=null)
					{
						if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
							Log.debugOut(Name()+" Landed and Stopped, and docking at "+CMLib.map().getExtendedRoomID(LR));
						//final CMMsg kMsg=CMClass.getMsg(msg.source(),getShipArea(),this,CMMsg.MSG_OK_ACTION,L("The ship comes to a resting stop."));
						dockHere(LR); // set location and so forth
					}
					else
					{
						if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
							Log.debugOut(Name()+" Landed and Stopped, but nowhere to dock. :(");
						// we landed, but there was nowhere to dock!
					}
				}
				else
				if(!amDestroyed())
				{
					if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
						Log.debugOut(Name()+" Collided with weird thing: "+msg.tool().ID());
				}
				else
				if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
					Log.debugOut(Name()+" was destroyed.");
				sendComputerMessage(mob,msg);
				break;
			}
			default:
				break;
			}
		}
	}

	@Override
	protected LocationRoom findNearestDocks(final Room R)
	{
		final List<LocationRoom> docks=new XVector<LocationRoom>();
		if(R!=null)
		{
			if((R.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
			&&(R instanceof LocationRoom))
				return (LocationRoom)R;

			TrackingLibrary.TrackingFlags flags;
			flags = CMLib.tracking().newFlags()
					.plus(TrackingLibrary.TrackingFlag.NOEMPTYGRIDS)
					.plus(TrackingLibrary.TrackingFlag.NOAIR)
					.plus(TrackingLibrary.TrackingFlag.NOHOMES)
					.plus(TrackingLibrary.TrackingFlag.UNLOCKEDONLY)
					.plus(TrackingLibrary.TrackingFlag.NOWATER);
			final List<Room> rooms=CMLib.tracking().getRadiantRooms(R, flags, 25);
			for(final Room R2 : rooms)
			{
				if((R2.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
				&&(R2 instanceof LocationRoom)
				&&(R.getArea().inMyMetroArea(R2.getArea())))
					docks.add((LocationRoom)R2);
			}
			if(docks.size()==0)
			{
				for(final Enumeration<Room> r=R.getArea().getMetroMap();r.hasMoreElements();)
				{
					final Room R2=r.nextElement();
					if((R2.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
					&&(R2 instanceof LocationRoom))
						docks.add((LocationRoom)R2);
				}
			}
			if(docks.size()==0)
			{
				for(final Room R2 : rooms)
				{
					if((R2.domainType()==Room.DOMAIN_OUTDOORS_SPACEPORT)
					&&(R2 instanceof LocationRoom))
						docks.add((LocationRoom)R2);
				}
			}
		}
		if(docks.size()==0)
			return null;
		return docks.get(CMLib.dice().roll(1, docks.size(), -1));
	}

	protected void stopThisShip(final MOB mob)
	{
		if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
			Log.debugOut("SpaceShip "+name()+" is now STOPPED!");
		setSpeed(0); // if you collide with something massive, your speed ENDS
		final List<Electronics> electronics=CMLib.tech().getMakeRegisteredElectronics(CMLib.tech().getElectronicsKey(getShipArea()));
		for(final Electronics E : electronics)
		{
			if(E instanceof ShipEngine)
			{
				final CMMsg msg=CMClass.getMsg(mob, E, this, CMMsg.NO_EFFECT, null, CMMsg.MSG_DEACTIVATE, null, CMMsg.NO_EFFECT,null);
				if(E.okMessage(mob, msg))
				{
					E.executeMsg(mob, msg);
					if(CMSecurity.isDebugging(DbgFlag.SPACESHIP))
						Log.debugOut("SpaceShip "+name()+" deactivated "+E.Name()+"!");
					((ShipEngine)E).setThrust(0.0);
				}
			}
		}
	}

	@Override
	public BoundedCube getBounds()
	{
		return new BoundedObject.BoundedCube(coordinates(),radius());
	}

	@Override
	public long powerCapacity()
	{
		return 0;
	}

	@Override
	public void setPowerCapacity(final long capacity)
	{
	}

	@Override
	public long powerRemaining()
	{
		return 0;
	}

	@Override
	public int powerNeeds()
	{
		return 0;
	}

	@Override
	public void setPowerRemaining(final long remaining)
	{
	}

	@Override
	public void activate(final boolean truefalse)
	{
	}

	protected void sendComputerMessage(final MOB mob, final CMMsg msg)
	{
		final Area ship=getShipArea();
		if(ship!=null)
		{
			final List<Electronics> electronics = CMLib.tech().getMakeRegisteredElectronics(CMLib.tech().getElectronicsKey(ship));
			for(final Electronics E : electronics)
			{
				if(E instanceof Computer)
				{
					if(E.owner() instanceof Room)
					{
						if(((Room)E.owner()).okMessage(mob, msg))
							((Room)E.owner()).send(mob, msg);
					}
					else
					if(E.okMessage(mob, msg))
						E.executeMsg(mob, msg);
				}
			}
		}
	}

	@Override
	public TechType getTechType()
	{
		return TechType.SHIP_SPACESHIP;
	}

	@Override
	public void setShipFlag(final ShipFlag flag, final boolean setFlag)
	{
		if(shipFlags.contains(flag))
		{
			if(!setFlag)
				shipFlags.remove(flag);
		}
		else
		{
			if(setFlag)
				shipFlags.add(flag);
		}
	}

	@Override
	public boolean getShipFlag(final ShipFlag flag)
	{
		return shipFlags.contains(flag);
	}

	@Override
	public SpaceObject getShipSpaceObject()
	{
		return this;
	}

	@Override
	public boolean activated()
	{
		return true;
	}

	@Override
	public int techLevel()
	{
		return phyStats().ability();
	}

	@Override
	public void setTechLevel(final int lvl)
	{
		basePhyStats.setAbility(lvl); recoverPhyStats();
	}

	@Override
	public String getManufacturerName()
	{
		return manufacturer;
	}

	@Override
	public void setManufacturerName(final String name)
	{
		cachedManufact=null;
		if(name!=null)
			manufacturer=name;
	}

	@Override
	public long getMass()
	{
		return basePhyStats().weight()+((area instanceof SpaceShip)?((SpaceShip)area).getMass(): 1000);
	}

	@Override
	public Manufacturer getFinalManufacturer()
	{
		if(cachedManufact==null)
		{
			cachedManufact=CMLib.tech().getManufacturerOf(this,getManufacturerName().toUpperCase().trim());
			if(cachedManufact==null)
				cachedManufact=CMLib.tech().getDefaultManufacturer();
		}
		return cachedManufact;
	}

	@Override
	public long[] coordinates()
	{
		return coordinates;
	}

	@Override
	public double[] direction()
	{
		return direction;
	}

	@Override
	public double roll()
	{
		return roll;
	}

	@Override
	public void setRoll(final double dir)
	{
		roll =dir;
	}

	@Override
	public double[] facing()
	{
		return facing;
	}

	@Override
	public void setFacing(final double[] dir)
	{
		if(dir!=null)
			this.facing=dir;
	}

	@Override
	public SpaceObject knownTarget()
	{
		return spaceTarget;
	}

	@Override
	public void setKnownTarget(final SpaceObject O)
	{
		spaceTarget=O;
	}

	@Override
	public void setCoords(final long[] coords)
	{
		if((coords!=null)&&(coords.length==3))
			CMLib.map().moveSpaceObject(this,coords);
	}

	@Override
	public void setDirection(final double[] dir)
	{
		if(dir!=null)
			direction=dir;
	}

	@Override
	public double speed()
	{
		return speed;
	}

	@Override
	public void setSpeed(final double v)
	{
		if(Double.isNaN(v))
		{
			Log.errOut("Bad Speed: "+v);
			return;
		}
		speed=v;
	}

	@Override
	public SpaceObject knownSource()
	{
		return (area instanceof SpaceObject)?((SpaceObject)area).knownSource():null;
	}

	@Override
	public void setKnownSource(final SpaceObject O)
	{
		if (area instanceof SpaceObject)
			((SpaceObject)area).setKnownSource(O);
	}

	@Override
	public long radius()
	{
		return (area instanceof SpaceObject)?((SpaceObject)area).radius():50;
	}

	@Override
	public void setRadius(final long radius)
	{
		if (area instanceof SpaceObject)
			((SpaceObject)area).setRadius(radius);
	}

	@Override
	public double getOMLCoeff()
	{
		return (area instanceof SpaceShip)?((SpaceShip)area).getOMLCoeff()
				:SpaceObject.ATMOSPHERIC_DRAG_STREAMLINE + ((SpaceObject.ATMOSPHERIC_DRAG_BRICK-SpaceObject.ATMOSPHERIC_DRAG_STREAMLINE)/2.0);
	}

	@Override
	public void setOMLCoeff(final double coeff)
	{
		if (area instanceof SpaceShip)
			((SpaceShip)area).setOMLCoeff(coeff);
	}

	private final static String[] MYCODES={"HASLOCK","HASLID","CAPACITY","CONTAINTYPES","RESETTIME","RIDEBASIS","MOBSHELD",
											"POWERCAP","ACTIVATED","POWERREM","MANUFACTURER","AREA","COORDS","RADIUS",
											"ROLL","DIRECTION","SPEED","FACING","OWNER","PRICE","DEFCLOSED","DEFLOCKED",
											"EXITNAME","TECHLEVEL",
											"PUTSTR","MOUNTSTR","DISMOUNTSTR","STATESTR","STATESUBJSTR","RIDERSTR"
										  };

	@Override
	public String getStat(final String code)
	{
		if(CMLib.coffeeMaker().getGenItemCodeNum(code)>=0)
			return CMLib.coffeeMaker().getGenItemStat(this,code);
		switch(getInternalCodeNum(code))
		{
		case 0:
			return "" + hasALock();
		case 1:
			return "" + hasADoor();
		case 2:
			return "" + capacity();
		case 3:
			return "" + containTypes();
		case 4:
			return "" + openDelayTicks();
		case 5:
			return "" + rideBasis();
		case 6:
			return "" + riderCapacity();
		case 7:
			return "" + powerCapacity();
		case 8:
			return "" + activated();
		case 9:
			return "" + powerRemaining();
		case 10:
			return getManufacturerName();
		case 11:
			return CMLib.coffeeMaker().getAreaObjectXML(getShipArea(), null, null, null, true).toString();
		case 12:
			return CMParms.toListString(coordinates());
		case 13:
			return "" + radius();
		case 14:
			return "" + roll();
		case 15:
			return CMParms.toListString(direction());
		case 16:
			return "" + speed();
		case 17:
			return CMParms.toListString(facing());
		case 18:
			return getOwnerName();
		case 19:
			return "" + getPrice();
		case 20:
			return "" + defaultsClosed();
		case 21:
			return "" + defaultsLocked();
		case 22:
			return "" + doorName();
		case 23:
			return "" + techLevel();
		case 24:
			return this.getPutString();
		case 25:
			return this.getMountString();
		case 26:
			return this.getDismountString();
		case 27:
			return this.getStateString();
		case 28:
			return this.getStateStringSubject();
		case 29:
			return this.getRideString();
		default:
			return CMProps.getStatCodeExtensionValue(getStatCodes(), xtraValues, code);
		}
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(CMLib.coffeeMaker().getGenItemCodeNum(code)>=0)
			CMLib.coffeeMaker().setGenItemStat(this,code,val);
		else
		switch(getInternalCodeNum(code))
		{
		case 0:
			setDoorsNLocks(hasADoor(), isOpen(), defaultsClosed(), CMath.s_bool(val), false, CMath.s_bool(val) && defaultsLocked());
			break;
		case 1:
			setDoorsNLocks(CMath.s_bool(val), isOpen(), CMath.s_bool(val) && defaultsClosed(), hasALock(), isLocked(), defaultsLocked());
			break;
		case 2:
			setCapacity(CMath.s_parseIntExpression(val));
			break;
		case 3:
			setContainTypes(CMath.s_parseBitLongExpression(Container.CONTAIN_DESCS, val));
			break;
		case 4:
			setOpenDelayTicks(CMath.s_parseIntExpression(val));
			break;
		case 5:
			break;
		case 6:
			break;
		case 7:
			setPowerCapacity(CMath.s_parseIntExpression(val));
			break;
		case 8:
			activate(CMath.s_bool(val));
			break;
		case 9:
			setPowerRemaining(CMath.s_parseLongExpression(val));
			break;
		case 10:
			setManufacturerName(val);
			break;
		case 11:
			setShipArea(val);
			break;
		case 12:
			setCoords(CMParms.toLongArray(CMParms.parseCommas(val, true)));
			coordinates[0] = coordinates[0] % SpaceObject.Distance.GalaxyRadius.dm;
			coordinates[1] = coordinates[1] % SpaceObject.Distance.GalaxyRadius.dm;
			coordinates[2] = coordinates[2] % SpaceObject.Distance.GalaxyRadius.dm;
			break;
		case 13:
			setRadius(CMath.s_long(val));
			break;
		case 14:
			setRoll(CMath.s_double(val));
			break;
		case 15:
			setDirection(CMParms.toDoubleArray(CMParms.parseCommas(val, true)));
			break;
		case 16:
			setSpeed(CMath.s_double(val));
			break;
		case 17:
			setFacing(CMParms.toDoubleArray(CMParms.parseCommas(val, true)));
			break;
		case 18:
			setOwnerName(val);
			break;
		case 19:
			setPrice(CMath.s_int(val));
			break;
		case 20:
			setDoorsNLocks(hasADoor(), isOpen(), CMath.s_bool(val), hasALock(), isLocked(), defaultsLocked());
			break;
		case 21:
			setDoorsNLocks(hasADoor(), isOpen(), defaultsClosed(), hasALock(), isLocked(), CMath.s_bool(val));
			break;
		case 22:
			doorName = val;
			break;
		case 23:
			setTechLevel(CMath.s_parseIntExpression(val));
			break;
		case 24:
			setPutString(val);
			break;
		case 25:
			setMountString(val);
			break;
		case 26:
			setDismountString(val);
			break;
		case 27:
			setStateString(val);
			break;
		case 28:
			setStateStringSubject(val);
			break;
		case 29:
			setRideString(val);
			break;
		default:
			CMProps.setStatCodeExtensionValue(getStatCodes(), xtraValues, code, val);
			break;
		}
	}

	private int getInternalCodeNum(final String code)
	{
		for(int i=0;i<MYCODES.length;i++)
		{
			if(code.equalsIgnoreCase(MYCODES[i]))
				return i;
		}
		return -1;
	}

	private static String[] codes=null;

	@Override
	public String[] getStatCodes()
	{
		if(codes!=null)
			return codes;
		final String[] MYCODES=CMProps.getStatCodesList(GenSpaceShip.MYCODES,this);
		final String[] superCodes=CMParms.toStringArray(GenericBuilder.GenItemCode.values());
		codes=new String[superCodes.length+MYCODES.length];
		int i=0;
		for(;i<superCodes.length;i++)
			codes[i]=superCodes[i];
		for(int x=0;x<MYCODES.length;i++,x++)
			codes[i]=MYCODES[x];
		return codes;
	}

	@Override
	public boolean sameAs(final Environmental E)
	{
		if(!(E instanceof GenSpaceShip))
			return false;
		final String[] codes=getStatCodes();
		for(int i=0;i<codes.length;i++)
		{
			if((!E.getStat(codes[i]).equals(getStat(codes[i])))
			&&(!codes[i].equals("AREA"))
			&&(!codes[i].equals("ABILITY")))
				return false;
		}
		final Area eA = ((GenSpaceShip)E).getShipArea();
		final Area A = this.getShipArea();
		final Enumeration<Room> er = eA.getProperMap();
		final Enumeration<Room> r = A.getProperMap();
		for(;r.hasMoreElements();)
		{
			final Room R=r.nextElement();
			if(!er.hasMoreElements())
				return false;
			final Room eR = er.nextElement();
			if(!R.sameAs(eR))
				return false;
			final Enumeration<Item> i=R.items();
			final Enumeration<Item> ei = eR.items();
			for(;i.hasMoreElements();)
			{
				final Item I=i.nextElement();
				if(!ei.hasMoreElements())
					return false;
				final Item eI=ei.nextElement();
				if(!I.sameAs(eI))
					return false;
			}
			if(ei.hasMoreElements())
				return false;
			final Enumeration<MOB> m=R.inhabitants();
			final Enumeration<MOB> em = eR.inhabitants();
			for(;m.hasMoreElements();)
			{
				final MOB M=m.nextElement();
				if(!em.hasMoreElements())
					return false;
				final MOB eM=em.nextElement();
				if(!M.sameAs(eM))
					return false;
			}
			if(em.hasMoreElements())
				return false;
		}
		if(er.hasMoreElements())
			return false;
		return true;
	}
}
