package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;

import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory; // 이렇게 필드 레벨로 뺼 수도 있다.

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em); // 필드 초기화

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        String qlString = "select m from Member m " +
                "where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
//        JPAQueryFactory queryFactory = new JPAQueryFactory(em); // JPAQueryFactory를 생성할 때, entitymanager를 같이 넘겨줘야 데이터를 찾을 때 활용할 수 있다.
//        QMember m = new QMember("m"); // 생성자 파라미터는 어떤 QMember인지 구분하기 위한 구분자로써 필수이다.(크게 중요하진 않다. 안 쓸 거기 때문)\
//        QMember m = QMember.member; // 기본 인스턴스 사용하는 법. 이렇게 하면 구분자 필요 x(querydsl이 기본 인스턴스를 만들어둔다.)

        /**
         * 권장법
         * static import 사용하기
         * 쿼리 작성 시, m 대신 QMember.member 사용
         * QMember.member는 static import로 member로 변경 가능
         */
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"), member.age.eq(10)) // and는 쉼표로도 가능
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() {
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // 카운트 쿼리
        Long count = queryFactory
                .select(member.count())
                .from(member)
                .fetchOne();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // offset : 출발 인덱스(인덱스는 0부터 존재) -> 1이면 두 번째 데이터부터 보여달라는 의미
                .limit(2) // 한 번에 몇 개씩 가져올 지 -> 2이면 2개씩 가져오라는 의미
                .fetch();

        Member member3 = result.get(0);
        Member member2 = result.get(1);

        assertThat(result.size()).isEqualTo(2);
        assertThat(member3.getUsername()).isEqualTo("member3");
        assertThat(member2.getUsername()).isEqualTo("member2");
    }

    /**
     * 전체 조회 수 (카운트 쿼리 날리면 된다)
     */
    @Test
    public void paging2() {
        Long totalCount = queryFactory
                .select(member.count())
                .from(member)
                // count 쿼리에는 orderBy, offset, limit 모두 필요 X
/*
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
*/
                .fetchOne();

        assertThat(totalCount).isEqualTo(4);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀 A에 소속된 모든 회원 조회
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인 (연관 관계가 없는 조인)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 세타 조인은 그냥 from절에 나열 (연관 관계가 없기 때문에 join을 사용할 필요 X)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
        assertThat(result.size()).isEqualTo(2);
    }

    /**
     * join on
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and t.name='teamA'
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM  Member m LEFT JOIN Team t ON m.username = t.name
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;
    /**
     * fetch join 적용 안 할 때
     */
    @Test
    public void fetchJoinNo() {
        // 페치 조인 테스트할 때, 영속성 컨텍스트에 남아있는 데이터들을 안 지워주면 결과를 제대로 보기 어렵기 때문에 영속성 컨텍스트 초기화한다.
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());// 조회한 회원과 연관되어있는 team이 현재 같이 로딩이 되었는지, 즉 같이 조회가 되었는지 확인하는 메서드(EntityManagerFactory 필요)

        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /**
     * fetch join 적용
     */
    @Test
    public void fetchJoinUse() {
        // 페치 조인 테스트할 때, 영속성 컨텍스트에 남아있는 데이터들을 안 지워주면 결과를 제대로 보기 어렵기 때문에 영속성 컨텍스트 초기화한다.
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());// 조회한 회원과 연관되어있는 team이 현재 같이 로딩이 되었는지, 즉 같이 조회가 되었는지 확인하는 메서드(EntityManagerFactory 필요)

        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /**
     * 서브 쿼리 (com.querydsl.jpa.JPAExpressions 사용)
     * 나이가 가장 많은 회원 조회 (eq 사용)
     */
    @Test
    public void subQuery() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max()) // 서브 쿼리와 메인 쿼리의 member가 겹치면 안 된다. 따라서 새로운 QMember 객체를 만들어줘야 한다.
                                .from(memberSub)

                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 서브 쿼리 (com.querydsl.jpa.JPAExpressions 사용)
     * 나이가 평균 이상인 회원 조회 (goe 사용)
     */
    @Test
    public void subQueryGoe() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg()) // 서브 쿼리와 메인 쿼리의 member가 겹치면 안 된다. 따라서 새로운 QMember 객체를 만들어줘야 한다.
                                .from(memberSub)

                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 서브 쿼리 (com.querydsl.jpa.JPAExpressions 사용)
     * 나이가 10살보다 많은 회원 조회(in 사용)
     */
    @Test
    public void subQueryIn() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age) // 서브 쿼리와 메인 쿼리의 member가 겹치면 안 된다. 따라서 새로운 QMember 객체를 만들어줘야 한다.
                                .from(memberSub)
                                .where(memberSub.age.gt(10))

                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * select절에 서브 쿼리
     */
    @Test
    public void selectSubQuery() {

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * CASE문
     * 간단한 조건
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * CASE문
     * 복잡한 조건 (CaseBuilder 사용)
     */
    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 상수 출력
     * Expressions.constant 사용
     * 특징 : JPQL에서는 따로 상수 조회 코드가 나가지 않는다.
     */
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자 더하기
     * concat() 사용 (concat()은 파라미터로 문자만 가능)
     */
    @Test
    public void concat() {
        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // .stringValue() : 숫자 타입인 나이를 문자 타입으로 변경
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    /**
     * 기본 JPQL로 DTO 조회
     */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * querydsl로 DTO 조회
     * 3가지 방법 존재
     * 1. 프로퍼티 접근(setter)
     * 2. 필드 직접 접근
     * 3. 생성자 사용
     */

    /**
     * 1. 프로퍼티 접근
     * setter를 통해 데이터를 주입하는 것
     * 단, 실제 프로퍼티명과 projection 필드명이 동일해야 한다.
     */
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class, // bean()을 하면 setter로 데이터를 injection 해준다.
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 2. 필드 접근
     * 필드에 바로 데이터를 주입하는 것
     * 즉, setter 없어도 데이터 주입 가능
     * 단, 실제 필드명과 projection 필드명이 동일해야 한다.
     */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class, // fields()를 하면 필드에 바로 데이터를 주입한다.
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 3. 생성자 접근
     * 객체의 생성자를 통해 데이터를 주입하는 것
     * 실제 생성자 파라미터의 타입과 일치해야 한다. (필드명은 달라도 됨)
     */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class, // constructor() 내의 파라미터 순서와 타입이 실제 생성자 파라미터의 순서와 타입과 일치해야 한다.
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * +) 필드 직접 접근
     * 실제 필드명과 projection 필드명이 다르다면?
     * UserDto의 이름 필드명은 name, projection 필드명은 username, 이 경우 이름 필드에는 데이터가 주입되지 않는다.(null 주입)
     * 이런 경우에는 as()를 사용하면 된다.
     */
    @Test
    public void findUserDto() {
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class, // fields()를 하면 필드에 바로 데이터를 주입한다.
                        member.username.as("name"),
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * @QueryProjection
     * DTO로 바로 조회하고 싶을 때, 해당 어노테이션을 DTO 클래스의 생성자에 붙이면 간단히 new 명령어를 통해 DTO를 조회할 수 있다.
     * 단, 해당 어노테이션을 붙인 후에 프로젝트를 다시 빌드해야 한다. (그래야 Q타입 DTO 객체가 생성되어 사용할 수 있다.)
     * new를 통해 생성자를 그대로 사용할 수 있기 때문에 타입 안정성에 있어서 이점이 있다. (타입 틀릴 일 없다는 얘기)
     *
     * 단점
     * 1. 번거로움 (직접 어노테이션을 추가해줘야 하고, 빌드도 다시 해줘야 한다.)
     * 2. 아키텍처 적인 문제? 의존 관계에 대한 문제? => MemberDto는 기존에 querydsl에 대한 의존 관계가 없었는데, 어노테이션을 추가함으로써 MemberDto가 querydsl에 대한 의존성이 형성됐다.
     *    이는 안정적인 구조가 아니다. 만약 querydsl을 제거하면, 해당 어노테이션은 필요없는 코드가 되고, MemberDto 코드도 같이 수정을 해야하기 때문이다.
     *    즉, DTO가 순수하지 않은 것. querydsl에 의존적으로 설계가 되는 것이다.
     *
     *    이건 결국 상황에 따라 유연하게 설계하면 된다.
     *    만약 DTO는 깔끔하게 가고 싶다면? 위에서 말한 필드나 프로퍼티 또는 생성자 주입 방식을 사용하면 된다.
     */
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리
     * 1. BoleanBuilder
     * 2. where 다중 파라미터 사용
     */

    /**
     * BooleanBuilder 사용한 동적 쿼리 생성
     */
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * where 다중 파라미터 사용한 동적 쿼리 생성
     * 영한님이 실무에서 즐겨 쓰는 방법
     * 이 방식을 쓰면 BooleanBuilder보다 훨씬 깔끔하다고 한다.
     * 단, 동적 쿼리를 위해서 null 체크는 반드시 해줘야 한다.
     *
     * 장점
     * 1. 조건 쿼리 메서드를 다른 쿼리에서도 재사용할 수 있다.
     * 2. 여러 메서드를 조합해서 어떤 특정 상태를 체크하는 조건 메서드를 생성할 수 있다. (pdf 참고)
     *    ex) 특정 상품이 할인 구매 가능한지 여부를 체크하는 메서드(isSellable)는 특정 상품의 재고가 있는지(isRemain)와 특정 상품에 할인을 적용할 수 있는지(isDiscount)를 합치면 된다.
     *     => where(isSellable(isRemain(...), isDiscount(...)))
     *    이렇게 하면 재사용할 수 있는 메서드들을 활용하여 특정 조건에 fit한 메서드를 생성할 수 있고, 이로 인해 쿼리 자체의 가독성이 높아지고 깔끔해진다.
     */
    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    // where 절 파라미터의 반환 타입은 Predicate보다는 BooleanExpression으로 해놓는 게 낫다.
    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond == null) {
            return null; // 이렇게 null을 반환하면 쿼리의 where절에 null이 대입된다. where절에 존재하는 null은 그냥 무시가 되기 때문에 동적 쿼리가 가능해지는 것이다.
        }
        return member.username.eq(usernameCond);
    }

    private BooleanExpression ageEq(Integer ageCond) {
        if (ageCond == null) {
            return null;
        }
        return member.age.eq(ageCond);
    }

    /**
     * 벌크 연산
     * 일반적인 업데이트 sql과 동일하다.
     *
     * !주의점!
     * 벌크 연산은 영속성 컨텍스트를 무시하고 DB에 바로 쿼리를 보낸다.
     * 따라서 벌크 연산 수행 후, DB는 바뀐 데이터가 존재하고, 영속성 컨텍스트에는 바뀌기 전의 데이터가 존재하여 서로의 상태가 달라지게 된다.
     * 이 상황에서 만약 트랜잭션이 끝나기 전에 다시 바뀐 데이터를 조회하게 된다면,
     * DB에서 바뀐 데이터를 가져오는 것이 아니라(가져오긴 하되, 다시 버린다.) 영속성 컨텍스트에 있는 바뀌지 않은 데이터를 가져오게 된다. (1차 캐시)
     *
     * #해결법
     * 벌크 연산 수행 후에는, 꼭 영속성 컨텍스트를 초기화해주자. (em.flush, em.clear)
     */
    @Test
    public void bulkUpdate() {
        long count = queryFactory // count : update 쿼리의 경우 변경된 row수가 반환된다.
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();
    }

    /**
     * 벌크 연산
     * 기존 숫자에 더하기 or 곱하기
     * ex) 모든 회원의 나이에 1살을 더해
     */
    @Test
    public void bulkAdd() {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // 빼고 싶으면 그냥 -1을 파라미터로 넣으면 된다. (minus 메서드는 존재하지 않기 때문)
                .execute();
    }

    /**
     * 벌크 연산
     * 삭제
     */
    @Test
    public void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /**
     * SQL function
     */
    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .where(member.username.eq(
                        Expressions.stringTemplate("function('lower', {0})", member.username)
                ))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
