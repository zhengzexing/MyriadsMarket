package com.atguigu.gulimall.member.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.gulimall.member.dao.MemberLevelDao;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UsernameExistException;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberRegistVo;
import com.atguigu.gulimall.member.vo.SocialUser;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;


@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {
    @Autowired
    MemberDao memberDao;

    @Autowired
    MemberLevelDao memberLevelDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void regist(MemberRegistVo vo) {
        MemberEntity memberEntity = new MemberEntity();
        //1.设置新建会员的等级
        MemberLevelEntity memberLevelEntity = memberLevelDao.getDefaultLevel();//获取会员等级表新建会员的默认等级
        memberEntity.setLevelId(memberLevelEntity.getId());

        //2.设置手机号，确保唯一性
        checkPhoneUnique(vo.getPhone());
        memberEntity.setMobile(vo.getPhone());

        //3.设置用户名，确保唯一性
        checkUserNameUnique(vo.getUserName());
        memberEntity.setUsername(vo.getUserName());

        //4.设置密码，进行加密存储(MD5加盐值，盐值存储在数据库太麻烦，于是使其自动生成盐值)
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        memberEntity.setPassword(passwordEncoder.encode(vo.getPassword()));

        //其他信息
        memberEntity.setNickname(vo.getUserName());

        memberDao.insert(memberEntity);
    }

    /**
     * 检查手机号是否唯一
     * @param phone
     * @return
     */
    @Override
    public void checkPhoneUnique(String phone) throws PhoneExistException{
        Integer mobileCount = memberDao.selectCount(
                new QueryWrapper<MemberEntity>()
                        .eq("mobile", phone)
        );
        if(mobileCount > 0){
            throw new PhoneExistException();
        }
    }

    /**
     * 检查用户名是否唯一
     * @param username
     * @return
     */
    @Override
    public void checkUserNameUnique(String username) throws UsernameExistException{
        Integer usernameCount = memberDao.selectCount(
                new QueryWrapper<MemberEntity>()
                        .eq("username", username)
        );
        if(usernameCount > 0){
            throw new UsernameExistException();
        }
    }

    /**
     * 账户登录
     * @param memberLoginVo
     * @return
     */
    @Override
    public MemberEntity login(MemberLoginVo memberLoginVo) {
        MemberEntity memberEntity = memberDao.selectOne(
                new QueryWrapper<MemberEntity>()
                        .eq("username", memberLoginVo.getLoginacct())
                        .or()
                        .eq("mobile", memberLoginVo.getLoginacct())
        );
        if(memberEntity == null){
            //未注册
            return null;
        }else{
            //注册了，进行密码配对
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            boolean result = passwordEncoder.matches(memberLoginVo.getPassword(), memberEntity.getPassword());
            if(result){
                //密码正确
                return memberEntity;
            }else{
                //密码错误
                return null;
            }
        }
    }

    /**
     * 用户通过社交账号进行登录，需要判断是否属于第一次登录
     * 若是第一次登录，需要注册账号，若不是，则直接登录
     * @param socialUser
     * @return
     */
    @Override
    public MemberEntity oauthLogin(SocialUser socialUser) throws Exception {
        //1.判断当前用户的uid在数据库中是否存在，若不存在则注册
        String uid = socialUser.getUid();
        MemberEntity socialMember = memberDao.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", uid));
        if(socialMember != null){
            //用户注册过，修改access_token授权码和过期时间即可
            socialMember.setAccessToken(socialUser.getAccess_token());
            socialMember.setExpiresIn(socialUser.getExpires_in());
            memberDao.updateById(socialMember);

            return socialMember;
        }else{
            //用户第一次使用该社交账号登录，需要注册
            MemberEntity register = new MemberEntity();

            //添加用户标识信息(必须)
            register.setSocialUid(socialUser.getUid());
            register.setAccessToken(socialUser.getAccess_token());
            register.setExpiresIn(socialUser.getExpires_in());
            register.setLevelId(1L);//普通会员

            try {
                //根据access_token授权码查询用户的其他信息(昵称，性别等 不必须，可选)
                Map<String,String> query = new HashMap<>();
                query.put("access_token", socialUser.getAccess_token());
                query.put("uid", socialUser.getUid());
                HttpResponse response = HttpUtils.doGet("https://api.weibo.com", "/2/users/show.json", "get", new HashMap<>(), query);
                if(response.getStatusLine().getStatusCode() == 200){
                    //根据access_token查询用户信息成功
                    String json = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = JSON.parseObject(json);
                    register.setNickname(jsonObject.getString("name"));
                    register.setGender("m".equalsIgnoreCase(jsonObject.getString("gender"))?1:0);
                }
            }catch (Exception e){
            }

            //注册
            memberDao.insert(register);

            return register;
        }
    }

}