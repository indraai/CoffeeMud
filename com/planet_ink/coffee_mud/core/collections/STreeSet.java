package com.planet_ink.coffee_mud.core.collections;

import java.io.Serializable;
import java.util.*;

/*
   Copyright 2010-2021 Bo Zimmerman

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
public class STreeSet<K> implements Serializable, Iterable<K>, Collection<K>, NavigableSet<K>, Set<K>, SortedSet<K>
{
	private static final long	serialVersionUID	= -6713012858869312626L;
	private volatile TreeSet<K>	T;

	public STreeSet()
	{
		T = new TreeSet<K>();
	}

	public STreeSet(final Comparator<? super K> comp)
	{
		T = new TreeSet<K>(comp);
	}

	public STreeSet(final List<K> E)
	{
		T = new TreeSet<K>();
		if (E != null)
		{
			this.T.addAll(E);
		}
	}

	public STreeSet(final K[] E)
	{
		T = new TreeSet<K>();
		if (E != null)
		{
			for (final K o : E)
				T.add(o);
		}
	}

	public STreeSet(final Enumeration<K> E)
	{
		T = new TreeSet<K>();
		if (E != null)
		{
			for (; E.hasMoreElements();)
				T.add(E.nextElement());
		}
	}

	public STreeSet(final Iterator<K> E)
	{
		T = new TreeSet<K>();
		if (E != null)
		{
			for (; E.hasNext();)
				T.add(E.next());
		}
	}

	public STreeSet(final Set<K> E)
	{
		T = new TreeSet<K>();
		if (E != null)
		{
			for (final K o : E)
				add(o);
		}
	}

	public synchronized void addAll(final Enumeration<K> E)
	{
		if (E != null)
		{
			for (; E.hasMoreElements();)
				T.add(E.nextElement());
		}
	}

	public synchronized void addAll(final K[] E)
	{
		if (E != null)
		{
			for (final K e : E)
				T.add(e);
		}
	}

	public synchronized void addAll(final Iterator<K> E)
	{
		if (E != null)
		{
			for (; E.hasNext();)
				T.add(E.next());
		}
	}

	public synchronized void removeAll(final Enumeration<K> E)
	{
		if (E != null)
		{
			for (; E.hasMoreElements();)
				T.remove(E.nextElement());
		}
	}

	public synchronized void removeAll(final Iterator<K> E)
	{
		if (E != null)
		{
			for (; E.hasNext();)
				T.remove(E.next());
		}
	}

	public synchronized void removeAll(final List<K> E)
	{
		if (E != null)
		{
			for (final K o : E)
				T.remove(o);
		}
	}

	@SuppressWarnings("unchecked")
	public synchronized TreeSet<K> toTreeSet()
	{
		return (TreeSet<K>) T.clone();
	}

	public synchronized Vector<K> toVector()
	{
		final Vector<K> V = new Vector<K>(size());
		for (final K k : T)
			V.add(k);
		return V;
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized boolean add(final K e)
	{
		T = (TreeSet<K>) T.clone();
		return T.add(e);
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized boolean addAll(final Collection<? extends K> c)
	{
		T = (TreeSet<K>) T.clone();
		return T.addAll(c);
	}

	@Override
	public synchronized K ceiling(final K e)
	{
		return T.ceiling(e);
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized void clear()
	{
		T = (TreeSet<K>) T.clone();
		T.clear();
	}

	@SuppressWarnings("unchecked")
	public synchronized STreeSet<K> copyOf()
	{
		final STreeSet<K> TS = new STreeSet<K>();
		TS.T = (TreeSet<K>) T.clone();
		return TS;
	}

	@Override
	public synchronized Comparator<? super K> comparator()
	{
		return T.comparator();
	}

	@Override
	public synchronized boolean contains(final Object o)
	{
		return T.contains(o);
	}

	@Override
	public synchronized Iterator<K> descendingIterator()
	{
		return new ReadOnlyIterator<K>(T.descendingIterator());
	}

	@Override
	public synchronized NavigableSet<K> descendingSet()
	{
		return T.descendingSet();
	}

	@Override
	public synchronized K first()
	{
		return T.first();
	}

	@Override
	public synchronized K floor(final K e)
	{
		return T.floor(e);
	}

	@Override
	public synchronized NavigableSet<K> headSet(final K toElement, final boolean inclusive)
	{
		return T.headSet(toElement, inclusive);
	}

	@Override
	public synchronized SortedSet<K> headSet(final K toElement)
	{
		return T.headSet(toElement);
	}

	@Override
	public synchronized K higher(final K e)
	{
		return T.higher(e);
	}

	@Override
	public synchronized boolean isEmpty()
	{
		return T.isEmpty();
	}

	@Override
	public synchronized Iterator<K> iterator()
	{
		return new ReadOnlyIterator<K>(T.iterator());
	}

	@Override
	public synchronized K last()
	{
		return T.last();
	}

	@Override
	public synchronized K lower(final K e)
	{
		return T.lower(e);
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized K pollFirst()
	{
		T = (TreeSet<K>) T.clone();
		return T.pollFirst();
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized K pollLast()
	{
		T = (TreeSet<K>) T.clone();
		return T.pollLast();
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized boolean remove(final Object o)
	{
		T = (TreeSet<K>) T.clone();
		return T.remove(o);
	}

	@Override
	public synchronized int size()
	{
		return T.size();
	}

	@Override
	public synchronized NavigableSet<K> subSet(final K fromElement, final boolean fromInclusive, final K toElement, final boolean toInclusive)
	{
		return T.subSet(fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	public synchronized SortedSet<K> subSet(final K fromElement, final K toElement)
	{
		return T.subSet(fromElement, toElement);
	}

	@Override
	public synchronized NavigableSet<K> tailSet(final K fromElement, final boolean inclusive)
	{
		return T.tailSet(fromElement, inclusive);
	}

	@Override
	public synchronized SortedSet<K> tailSet(final K fromElement)
	{
		return T.tailSet(fromElement);
	}

	@Override
	public boolean equals(final Object arg0)
	{
		return this == arg0;
	}

	@Override
	public int hashCode()
	{
		return super.hashCode();
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized boolean removeAll(final Collection<?> arg0)
	{
		T = (TreeSet<K>) T.clone();
		return T.removeAll(arg0);
	}

	@Override
	public synchronized boolean containsAll(final Collection<?> arg0)
	{
		return T.containsAll(arg0);
	}

	@SuppressWarnings("unchecked")

	@Override
	public synchronized boolean retainAll(final Collection<?> arg0)
	{
		T = (TreeSet<K>) T.clone();
		return T.retainAll(arg0);
	}

	@Override
	public synchronized Object[] toArray()
	{
		return T.toArray();
	}

	@Override
	public synchronized <T> T[] toArray(final T[] arg0)
	{
		return T.toArray(arg0);
	}

	@Override
	public String toString()
	{
		return super.toString();
	}

}
