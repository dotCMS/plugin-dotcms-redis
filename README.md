# dotCMS & Redis

This static (not osgi) plugin will override the internal dotcms caching infrastructure (Guava) and replace it with redis, an external caching mechanism.  This plugin is provided "as is" and any implementation of it should go through extensive testing before using it in a production environment.

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
You can specify different servers to read from and to write to. This allows you to set up Redis in a master / slave configuration and write to the master and read from a slave.


## Testing
To test, fire up a redis server locally, on port 6379 (should be the default):
```
redis-server
```




## Library

The library used in this implementation is called Jedis.  Jedis uses a configurable pool of connections to access the Redis server.


