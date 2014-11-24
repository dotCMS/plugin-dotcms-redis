package com.dotcms.plugin.redis.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import com.dotcms.repackage.com.google.common.cache.Cache;
import com.dotcms.repackage.org.apache.commons.collections.map.LRUMap;
import com.dotmarketing.business.DotCacheAdministrator;
import com.dotmarketing.business.DotCacheException;
import com.dotmarketing.business.DotGuavaCacheAdministratorImpl;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

public class RedisCacheAdministratorImpl extends DotGuavaCacheAdministratorImpl {

	private final ConcurrentHashMap<String, Cache<String, Object>> groups = new ConcurrentHashMap<String, Cache<String, Object>>();

	long lastError = 0;

	static private JedisPool readPool;
	static private JedisPool writePool;

	final char delimit = ';';

	private Map<String, Object> cacheStatus;

	@Override
	public Object getMemory(String key, String group) throws DotCacheException {

		return super.getMemory(key, group);
	}

	@Override
	public Object getDisk(String key, String group) throws DotCacheException {
		return super.getDisk(key, group);
	}

	@Override
	public List<Map<String, Object>> getCacheStatsList() {

		return super.getCacheStatsList();
	}

	@Override
	public String getCacheStats() {

		return super.getCacheStats();
	}

	public RedisCacheAdministratorImpl() {
		// super();
		String writeHost = Config.getStringProperty("redis.server.write.address",
				Config.getStringProperty("redis.server.address", Protocol.DEFAULT_HOST));
		int writePort = Config.getIntProperty("redis.server.write.port", Config.getIntProperty("redis.server.port", Protocol.DEFAULT_PORT));
		int timeout = Config.getIntProperty("redis.server.timeout", Protocol.DEFAULT_TIMEOUT);
		JedisPoolConfig conf = new JedisPoolConfig();
		
		int maxClients = Config.getIntProperty("redis.pool.max.clients", 100);
		int maxIdle = Config.getIntProperty("redis.pool.max.idle", 20);
		int minIdle = Config.getIntProperty("redis.pool.min.idle", 5);
		boolean testReturn = Config.getBooleanProperty("redis.pool.test.on.return", false);
		boolean blockExhausted = Config.getBooleanProperty("redis.pool.block.when.exhausted", false);
		String redisPass = Config.getStringProperty("redis.password", null);


						
		conf.setMaxTotal(maxClients);
		conf.setMaxIdle(maxIdle);
		conf.setTestOnReturn(testReturn);
		conf.setMinIdle(minIdle);
		conf.setBlockWhenExhausted(blockExhausted);

		
		
		

		writePool = (redisPass==null)  
			? new JedisPool(conf, writeHost, writePort, timeout)
			: new JedisPool(conf, writeHost, writePort, timeout, redisPass);

		String readHost = Config.getStringProperty("redis.server.read.address",
				Config.getStringProperty("redis.server.address", Protocol.DEFAULT_HOST));
		int readPort = Config.getIntProperty("redis.server.read.port", Config.getIntProperty("redis.server.port", Protocol.DEFAULT_PORT));

		if (readHost.equals(writeHost) && readPort == writePort) {
			readPool = writePool;
		} else {
			readPool = (redisPass==null)  
				? new JedisPool(conf, readHost, readPort, timeout)
				: new JedisPool(conf, readHost, readPort, timeout, redisPass);
		}


	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.dotmarketing.business.DotCacheAdministrator#flushAll()
	 */
	public void flushAll() {
		flushAlLocalOnlyl();
		// super.flushAll();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.dotmarketing.business.DotCacheAdministrator#flushGroup(java.lang.
	 * String)
	 */

	public void flushGroup(String group) {
		// super.flushGroup(group);
		flushGroupLocalOnly(group);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.dotmarketing.business.DotCacheAdministrator#flushAll()
	 */
	public void flushAlLocalOnlyl() {
		Jedis jed = null;
		// super.flushAlLocalOnlyl();
		try {
			jed = writePool.getResource();
			jed.flushAll();
			writePool.returnResource(jed);

		} catch (Exception e) {
			slowLogger(e, false);
		}
	}

	public void flushGroupLocalOnly(String group) {
		// super.flushGroupLocalOnly(group);
		if (group == null) {
			return;
		}
		group = group.toLowerCase();
		Set<String> names = getKeys(group);
		Iterator<String> it = names.iterator();
		Jedis jed = null;

		try {
			jed = writePool.getResource();
			boolean go = it.hasNext();
			List<String> del = new ArrayList<String>();
			while (go) {
				while (it.hasNext()) {
					del.add(it.next());
					if (del.size() > 100) {
						jed.del(del.toArray(new String[del.size()]));
						del = new ArrayList<String>();
						break;
					}
				}
				jed.del(del.toArray(new String[del.size()]));
				break;
			}



		} catch (Exception e) {
			slowLogger(e, false);
		}
		finally{
			if(jed!=null){
				writePool.returnResource(jed);
			}
		}

	}

	/**
	 * Gets from Memory, if not in memory, tries disk
	 */
	public Object get(String key, String group) throws DotCacheException {

		if (key == null || group == null) {
			return null;
		}
		

		StringWriter k = new StringWriter();
		k.append(key.toLowerCase());
		k.append(delimit);
		k.append(group.toLowerCase());

		

		Jedis jed = null;
		byte[] data = null;
		try {
			jed = readPool.getResource();
			data = jed.get(k.toString().getBytes());
		} catch (Exception e) {
			slowLogger(e, false);
		} finally {
			try {
				readPool.returnResource(jed);
			} catch (Exception e) {
				// die quiet
			}

		}

		if (data == null) {
			return null;
		}

		ObjectInputStream input = null;
		InputStream bin = null;
		InputStream is = null;

		try {
			is = new ByteArrayInputStream(data);
			bin = new BufferedInputStream(is, 8192);
			input = new ObjectInputStream(bin);
			Object obj = input.readObject();
			// Logger.info(this.getClass(), "redis: " + k.toString() +"=" +
			// obj.getClass());
			return obj;
		} catch (Exception e) {
			Logger.error(this.getClass(), e.getMessage());
			Logger.error(this.getClass(), "key:" + key + " group:" + group);

		} finally {
			try {
				if (input != null)
					input.close();

			} catch (Exception e) {
				// die quiet
			}
			try {
				if (bin != null)
					bin.close();

			} catch (Exception e) {
				// die quiet
			}
			try {
				if (is != null)
					is.close();

			} catch (Exception e) {
				// die quiet
			}
		}
		return null;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.dotmarketing.business.DotCacheAdministrator#put(java.lang.String,
	 * java.lang.Object, java.lang.String[])
	 */
	public void put(String key, final Object content, String group) {
		if (key == null || group == null) {
			return;
		}
		
		// velocity macros cannot be cached
		if(group.equals("VelocityCache") && key.endsWith(".vm")){
			return;
		}
		StringWriter k = new StringWriter();
		k.append(key.toLowerCase());
		k.append(delimit);
		k.append(group.toLowerCase());

		Jedis jed = null;
		ObjectOutputStream output = null;
		OutputStream bout = null;
		try {
			jed = writePool.getResource();

			ByteArrayOutputStream os = new ByteArrayOutputStream();

			bout = new BufferedOutputStream(os, 8192);

			output = new ObjectOutputStream(bout);
			output.writeObject(content);
			output.flush();
			byte[] data = os.toByteArray();

			if (data == null || data.length == 0)
				return;

			// String x = new String(bytes.toByteArray());
			jed.set(k.toString().getBytes(), data);
		} catch (Exception e) {
			slowLogger(e, false);
		} finally {
			try {
				if (output != null)
					output.close();

			} catch (Exception e) {
				// die quiet
			}
			try {
				if (output != null)
					output.close();

			} catch (Exception e) {
				// die quiet
			}
			try {
				if (jed != null)
					writePool.returnResource(jed);

			} catch (Exception e) {
				// die quiet
			}

		}

	}

	private void slowLogger(Exception e, boolean stack) {
		if (System.currentTimeMillis() - lastError > 10000) {
			lastError = System.currentTimeMillis();
			Logger.error(this.getClass(), "---- Redis is deadis:--- vvvvvv");
			Logger.error(this.getClass(), "---- Redis is deadis:" + Thread.currentThread().getStackTrace()[2]);
			if (stack) {
				Logger.error(this.getClass(), "---- Redis is deadis:" + e.getMessage(), e);
			} else {
				Logger.error(this.getClass(), "---- Redis is deadis:" + e.getMessage());
			}
			Logger.error(this.getClass(), "---- Redis is deadis:--- ^^^^^^");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.dotmarketing.business.DotCacheAdministrator#remove(java.lang.String)
	 */
	public void remove(final String key, final String group) {
		if (key == null || group == null) {
			return;
		}
		Runnable cacheRemoveRunnable = new Runnable() {
			public void run() {

				String k = key.toLowerCase();
				String g = group.toLowerCase();
				removeLocalOnly(k, g);

			}
		};
		try {
			if (!DbConnectionFactory.getConnection().getAutoCommit()) {
				HibernateUtil.addCommitListener(cacheRemoveRunnable);
			}
		} catch (Exception e) {
			Logger.error(DotGuavaCacheAdministratorImpl.class, e.getMessage(), e);
		}
		cacheRemoveRunnable.run();
	}

	/*
	 * This method should only be called by Jgroups because it doesn't handle
	 * any local transaction as the remove does.
	 */
	public void removeLocalOnly(final String key, final String group) {
		if (key == null || group == null) {
			return;
		}
		Runnable cacheRemoveRunnable = new Runnable() {
			public void run() {
				StringWriter k = new StringWriter();
				k.append(key.toLowerCase());
				k.append(delimit);
				k.append(group.toLowerCase());
				Jedis jed = null;
				try {
					jed = writePool.getResource();
					jed.del(k.toString());

					writePool.returnResource(jed);
				} catch (Exception e) {
					slowLogger(e, false);
				}

			}
		};
		cacheRemoveRunnable.run();
	}

	public Set<String> getKeys(String group) {
		if (group == null) {
			return null;
		}

		group = group.toLowerCase();
		Jedis jed = null;

		try {
			jed = readPool.getResource();
			return jed.keys(group + "*");
		} catch (Exception e) {
			slowLogger(e, false);
		} finally {
			readPool.returnResource(jed);
		}
		return new HashSet<String>();

	}

	private class CacheComparator implements Comparator<Map<String, Object>> {
		static final String LIVE_CACHE_PREFIX = "livecache";
		static final String WORKING_CACHE_PREFIX = "workingcache";

		public int compare(Map<String, Object> o1, Map<String, Object> o2) {

			if (o1 == null && o2 != null)
				return 1;
			if (o1 != null && o2 == null)
				return -1;
			if (o1 == null && o2 == null)
				return 0;

			String group1 = (String) o1.get("region");
			String group2 = (String) o2.get("region");

			if (!UtilMethods.isSet(group1) && !UtilMethods.isSet(group2)) {
				return 0;
			} else if (UtilMethods.isSet(group1) && !UtilMethods.isSet(group2)) {
				return -1;
			} else if (!UtilMethods.isSet(group1) && UtilMethods.isSet(group2)) {
				return 1;
			} else if (group1.equals(group2)) {
				return 0;
			} else if (group1.startsWith(WORKING_CACHE_PREFIX) && group2.startsWith(LIVE_CACHE_PREFIX)) {
				return 1;
			} else if (group1.startsWith(LIVE_CACHE_PREFIX) && group2.startsWith(WORKING_CACHE_PREFIX)) {
				return -1;
			} else if (!group1.startsWith(LIVE_CACHE_PREFIX) && !group1.startsWith(WORKING_CACHE_PREFIX)
					&& (group2.startsWith(LIVE_CACHE_PREFIX) || group2.startsWith(WORKING_CACHE_PREFIX))) {
				return -1;
			} else if ((group1.startsWith(LIVE_CACHE_PREFIX) || group1.startsWith(WORKING_CACHE_PREFIX))
					&& !group2.startsWith(LIVE_CACHE_PREFIX) && !group2.startsWith(WORKING_CACHE_PREFIX)) {
				return 1;
			} else { // neither group1 nor group2 are live or working
				return group1.compareToIgnoreCase(group2);
			}
		}

	}

	public void shutdown() {
		//super.shutdown();
		writePool.destroy();
		readPool.destroy();
	}

	@Override
	public Class getImplementationClass() {
		return RedisCacheAdministratorImpl.class;
	}

	@Override
	public DotCacheAdministrator getImplementationObject() {
		return this;
	}

}
