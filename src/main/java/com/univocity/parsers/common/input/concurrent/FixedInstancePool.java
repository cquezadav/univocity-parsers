/*******************************************************************************
 * Copyright 2014 uniVocity Software Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.univocity.parsers.common.input.concurrent;

/**
 * A very simple object instance pool with a fixed size.
 *
 * <p> This is essentially an immutable circular queue. Elements are not added nor removed. Pointers to the head and tail of the queue identify what is the next available entry.
 * <p> Use {@link FixedInstancePool#allocate()} to get an available {@link Entry} from the pool. If all objects are allocated then the thread will block until an element is released.
 * <p> {@link FixedInstancePool#release(Entry)} releases an allocated {@link Entry} for reuse.
 *
 * @param <T> the class of objects stored in the instance pool
 *
 * @see Entry
 *
 * @author uniVocity Software Pty Ltd - <a href="mailto:parsers@univocity.com">parsers@univocity.com</a>
 *
 */
abstract class FixedInstancePool<T> {
	final Entry<T>[] instancePool;
	private final int[] instanceIndexes;
	private int head = 0;
	private int tail = 0;
	int count = 0;

	/**
	 * Creates a new instance pool with the given size. Upon instantiation, the {@link FixedInstancePool#newInstance()} method will be called to fill in the instance pool, and the pool
	 * can then have its entries allocated for use (and reuse).
	 * @param size the size of the fixed instance pool.
	 */
	@SuppressWarnings("unchecked")
	FixedInstancePool(int size) {
		instancePool = new Entry[size];
		instanceIndexes = new int[size];
		for (int i = 0; i < size; i++) {
			instancePool[i] = new Entry<T>(newInstance(), i);
			instanceIndexes[i] = i;
		}
	}

	/**
	 * Creates a new instance of the given type of objects stored as entries of this instance pool
	 * This method is called in the constructor of this class for initialization of the instance array and must always return a new instance.
	 * @return returns a new instance to use in the pool
	 */
	protected abstract T newInstance();

	/**
	 * Retrieves the next available entry in this instance pool. Blocks until an entry becomes available (through {@link FixedInstancePool#release(Entry)}).
	 * @return the next available entry in this instance pool
	 */
	public synchronized Entry<T> allocate() {
		while (count == instancePool.length) {
			try {
				wait(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Thread interrupted", e);
			}
		}
		if (count == instancePool.length) {
			// in case a timeout happens
			return new Entry<T>(newInstance(), -1);
		}

		Entry<T> out = instancePool[instanceIndexes[head]];
		// instanceIndexes[head] = -1; //enable to print the queue's contents for debugging purposes
		head = (head + 1) % instancePool.length;
		count++;
		return out;
	}

	/**
	 * Releases the given entry and makes it available for allocation (by {@link FixedInstancePool#allocate()})
	 * @param e the entry to be released and made available for reuse.
	 */
	public synchronized void release(Entry<T> e) {
		if (e.index != -1) {
			instanceIndexes[tail] = e.index;
			tail = (tail + 1) % instancePool.length;
			count--;
		}
		notify();
	}
}
