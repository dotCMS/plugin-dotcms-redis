# Dotcms & Redis

This plugin will override the internal dotcms caching infrastructure (Guava) and replace it with redis, an external caching mechanism.  This plugin is provided "as is" and any implementation of it should go through extensive testing before using it in a production environment.

## Benefits

The benefits of redis are multiple:
* the caching infrastructure becomes a networked server (or cluster) itself 
* the caching server can be scaled independently and at runtime in order to add/remove cache capacity.  
* Removes Dotcms cache from the java heap, which should lower Dotcms memory requirement and speed up GC significantly.
* Cache "puts" and "removes" are network-wide - there is no external syncing mechanism that can fail.
* Dotcms servers can be restarted with their caches fully loaded, which can be a boon for large implementations.
* Amazon offers a cloud based redis infrastructure called [Amazon ElastiCache](http://aws.amazon.com/elasticache) that can scale your cache in the cloud

## About Redis
Redis needs to be configured externally to dotcms and there is a lot to learn in running a Redis server.  For more information, see: http://Redis.io

## Configuring
You point to a redis server/port by using the [dotmarketing-config.properties](https://github.com/dotCMS/plugin-dotcms-redis/blob/master/conf/dotmarketing-config-ext.properties) 

Hopefully, the [config](https://github.com/dotCMS/plugin-dotcms-redis/blob/master/conf/dotmarketing-config-ext.properties) is self documenting.

## Master / Slave support





## Testing
To test, fire up a redis server locally, on port 6379 (should be the default):
```
redis-server
```

Then deploy this plugin, start dotcms and volia, keys starting to fill your
redis server.  
```
memcached -d
```


## Library

The library used in this implementation is called XMemcached.  You can read more about it
here:

https://code.google.com/p/xmemcached/


## Adding and removing servers from the memcached cluster
The code for adding or removing servers from the memcached cluster looks like this:

```
MemcachedCacheAdministratorImpl mem = (MemcachedCacheAdministratorImpl) CacheLocator.getCacheAdministrator().getImplementationObject();
mem.addServer("127.0.0.1:11212", 5);
mem.removeServer("127.0.0.1:11212");
```
