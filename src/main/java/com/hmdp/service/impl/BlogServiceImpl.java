package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserServiceImpl userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Integer id) {
        //从数据库查询笔记
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //查询用户信息
        queryUser(blog);
        isLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryUser(blog);
            isLiked(blog);
        });
        return Result.ok(records);
    }

    //判断这个博客是否被当前用户点赞
    private void isLiked(Blog blog){
        // 1.查询当前用户
        UserDTO user = UserHolder.getUser();
        if(user == null){     //用户未登录直接返回就行
            return;
        }
        Long userId = user.getId();
        // 2.判断当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        if(score!=null){
            blog.setIsLike(true);
        }else{
            blog.setIsLike(false);
        }
    }


    //点赞
    @Override
    public Result likeBlog(Long id) {
        // 1.查询当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if(score == null){
            // 3.如果没点赞
            // 3.1点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2记录下点赞的用户id
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(),System.currentTimeMillis());
            }
        }else{
            // 4.如果点赞了
            // 4.1点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2删除点赞的用户id
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }

    //根据博客的id  查询有哪些用户点赞了
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //对查到的前五名进行排序
        if(top5==null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //存在点赞
        //1. 解析出用户的id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 2. 根据用户id查询用户信息
        String idsStr = StringUtils.join(ids, ",");
        List<UserDTO> users = userService.query().in("id", ids)
                .last("order by FIELD(id," + idsStr + ")")
                .list().stream().map(
                        user -> BeanUtil.copyProperties(user, UserDTO.class)
                ).collect(Collectors.toList());
        // 3. 返回
        return Result.ok(users);

    }

    private void queryUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
