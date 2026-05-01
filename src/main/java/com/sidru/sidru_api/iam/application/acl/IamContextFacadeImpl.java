package com.sidru.sidru_api.iam.application.acl;

import com.sidru.sidru_api.iam.domain.model.queries.GetUserByEmailQuery;
import com.sidru.sidru_api.iam.domain.model.queries.GetUserByIdQuery;
import com.sidru.sidru_api.iam.domain.services.UserQueryService;
import com.sidru.sidru_api.iam.interfaces.acl.IamContextFacade;
import io.jsonwebtoken.lang.Strings;
import org.springframework.stereotype.Service;

@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserQueryService userQueryService;

    public IamContextFacadeImpl(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public Long fetchUserIdByEmail(String email) {
        var getUserByEmailQuery = new GetUserByEmailQuery(email);
        var result = userQueryService.handle(getUserByEmailQuery);
        if (result.isEmpty()) return 0L;
        return result.get().getId();
    }

    @Override
    public String fetchEmailByUserId(Long userId) {
        var getUserByIdQuery = new GetUserByIdQuery(userId);
        var result = userQueryService.handle(getUserByIdQuery);
        if (result.isEmpty()) return Strings.EMPTY;
        return result.get().getEmail();
    }

    @Override
    public Boolean checkUserExistsByUserId(Long userId) {
        var getUserByIdQuery = new GetUserByIdQuery(userId);
        var result = userQueryService.handle(getUserByIdQuery);
        return result.isPresent();
    }
}
