grammar GMParser;

header {
}

/*class gm_parser extends Parser;*/
options {
    language   = Java;
    output     = AST;
    superClass = AbstractGMParser;
    tokenVocab = GMLexer;
}

/*
%{
    #include <stdio.h>
    #include <string.h>
    #include <assert.h>
    #include 'gm_frontend_api.h'
    #define YYERROR_VERBOSE 1
    extern void   GM_lex_begin_user_text();

    extern void yyerror(const char* str);
    extern int yylex();
%}
*/
/* Reserved Words */
/* operator precedence, Lower is higher */
/* %glr-parser */
prog
    :   proc_def*
    ;


proc_def
    :   proc_head
        proc_body
    ;


proc_head
    :   proc_name
        '(' arg_declist? ')'
        proc_return?
    |   proc_name
        '(' arg_declist? ';' arg_declist ')'
        proc_return?
    ;


proc_name
    :   T_PROC  id
    |   T_LOCAL id
    ;


arg_declist
    :   arg_decl ( ',' arg_decl )*
    ;


proc_return
    :   ':' prim_type
        /* return of function should be always primitive type */
    |   ':' node_type
    /*| ':' graph_type */
    ;


arg_decl
    :   arg_target ':' typedecl
    ;


arg_target
    :   id_comma_list
    ;


typedecl
    :   prim_type
    |   graph_type
    |   property
    |   nodeedge_type
    |   set_type
    ;


graph_type
    :   T_GRAPH
    ;


prim_type
    :   T_INT
    |   T_LONG
    |   T_FLOAT
    |   T_DOUBLE
    |   T_BOOL
    ;


nodeedge_type
    :   node_type
    |   edge_type
    ;


node_type
    :   T_NODE
        ( '(' id ')' )?
    ;


edge_type
    :   T_EDGE
        ( '(' id ')' )?
    ;


set_type
    :   T_NSET
        ( '(' id ')' )?
    |   T_NSEQ
        ( '(' id ')' )?
    |   T_NORDER
        ( '(' id ')' )?
    |   T_COLLECTION
    	'<' set_type '>'
    	( '(' id ')' )?
    ;


property
    :   T_NODEPROP '<' prim_type '>'
        ( '(' id ')' )?
    |   T_NODEPROP '<' nodeedge_type '>'
        ( '(' id ')' )?
    |   T_NODEPROP '<' set_type '>'
        ( '(' id ')' )?
    |   T_EDGEPROP '<' prim_type '>'
        ( '(' id ')' )?
    |   T_EDGEPROP '<' nodeedge_type '>'
        ( '(' id ')' )?
    |   T_EDGEPROP '<' set_type '>'
        ( '(' id ')' )?
    ;


id_comma_list
    :   id ( ',' id )*
    ;


proc_body
    :   sent_block
    ;


sent_block
    :   sb_begin
        sent_list
        sb_end
    ;


sb_begin
    :   '{'
    ;


sb_end
    :   '}'
    ;


sent_list
    :   sent*
    ;


sent
    :   sent_assignment ';'
    |   sent_variable_decl ';'
    |   sent_block
    |   sent_foreach
    |   sent_if
    |   sent_reduce_assignment ';'
    |   sent_defer_assignment ';'
    |   sent_do_while ';'
    |   sent_while
    |   sent_return ';'
    |   sent_bfs
    |   sent_dfs
    |   sent_call ';'
    |   sent_user ';'
    |   sent_argminmax_assignment ';'
    |   ';'
    ;


sent_call
    :   built_in
    ;


sent_while
    :   T_WHILE
        '(' bool_expr ')'
        sent_block
    ;


sent_do_while
    :   T_DO
        sent_block
        T_WHILE
        '(' bool_expr ')'
    ;


sent_foreach
    :   T_FOREACH
        foreach_header
        foreach_filter?
        sent
    |   T_FOR
        foreach_header
        foreach_filter?
        sent
    ;


foreach_header
    :   '(' id ':' id     '.' iterator1 ')'
    |   '(' id ':' id '+' '.' iterator1 ')'
    |   '(' id ':' id '-' '.' iterator1 ')'
    ;


foreach_filter
    :   '(' bool_expr ')'
    ;


iterator1
    :   T_NODES
    |   T_EDGES
    |   T_NBRS
    |   T_IN_NBRS
    |   T_UP_NBRS
    |   T_DOWN_NBRS
    |   T_ITEMS
    |   T_COMMON_NBRS '(' id ')'
    ;


sent_dfs
    :   T_DFS
    	bfs_header_format
    	bfs_filters?
    	sent_block
    	dfs_post?
    ;


sent_bfs
    :   T_BFS
    	bfs_header_format
    	bfs_filters?
    	sent_block
    	bfs_reverse?
    ;


dfs_post
    :   T_POST
        bfs_filter?
        sent_block
    ;


bfs_reverse
    :   T_BACK
        bfs_filter?
        sent_block
    ;


bfs_header_format
    :   '(' id ':' id '^'? '.' T_NODES from_or_semi id ')'
    ;


from_or_semi
    :   T_FROM
    |   ';'
    ;


bfs_filters
    :   bfs_navigator
    |   bfs_filter
    |   bfs_navigator bfs_filter
    |   bfs_filter    bfs_navigator
    ;


bfs_navigator
    :   '[' expr ']'
    ;


bfs_filter
    :   '(' expr ')'
    ;


sent_variable_decl
    :   typedecl
    	var_target
    |   typedecl
        id
        '='
        rhs
    ;


var_target
    :   id_comma_list
    ;


sent_assignment
    :   lhs '=' rhs
    ;


sent_reduce_assignment
    :   lhs
    	reduce_eq
    	rhs
    	optional_bind
    |   lhs
        T_PLUSPLUS
        optional_bind
    ;


sent_defer_assignment
    :
    lhs
    T_LE
    rhs
    optional_bind
    ;


sent_argminmax_assignment
    :
    lhs_list2
    minmax_eq
    rhs_list2
    optional_bind
    ;


optional_bind
    :   ( '@' id )?
    ;


reduce_eq
    :   T_PLUSEQ
    |   T_MULTEQ
    |   T_MINEQ
    |   T_MAXEQ
    |   T_ANDEQ
    |   T_OREQ
    ;


minmax_eq
    :   T_MINEQ
    |   T_MAXEQ
    ;


rhs
    :   expr
    ;


sent_return
    :   T_RETURN
    	expr
    |   T_RETURN
        /* This causes a shift-reduce conflict: What would be If (x) If (y) Else z;
   * The default action is to interpret it as If (x) {If (y) Else z;}, which is what C does.
   * */
    ;


sent_if
    :   T_IF '(' bool_expr ')'
        sent
        ( T_ELSE sent )?
    ;


sent_user
    :   expr_user
        ( T_DOUBLE_COLON '[' lhs_list ']' )?
    ;


expr
    :
(
        '(' expr ')'
    |   '|' expr '|'
    |   '-' expr
    |   '!' expr
    |   '(' prim_type ')' expr
    |   reduce_op 
        '(' id ':' id '.' iterator1 ')'
        ( '(' expr ')' )?
        '{' expr '}'
    |   reduce_op2
        '(' id ':' id '.' iterator1 ')'
        ( '(' expr ')' )?
    |   expr '%'   expr
    |   expr '*'   expr
    |   expr '/'   expr
    |   expr '+'   expr
    |   expr '-'   expr
    |   expr T_LE  expr
    |   expr T_GE  expr
    |   expr '<'   expr
    |   expr '>'   expr
    |   expr T_EQ  expr
    |   expr T_NEQ expr
    |   expr T_AND expr
    |   expr T_OR  expr
    |   expr '?'   expr ':' expr
    |   BOOL_VAL
    |   INT_NUM
    |   FLOAT_NUM
    |   inf
    |   T_NIL
    |   scala
    |   field
    |   built_in
    |   expr_user
        /* cannot be distinguished by the syntax, until type is available. due to vars */
)*
    ;


bool_expr
    :   expr
    ;


numeric_expr
    :   expr
    ;


reduce_op
    :   T_SUM
    |   T_PRODUCT
    |   T_MIN
    |   T_MAX
    |   T_EXIST
    |   T_ALL
    |   T_AVG
    ;


reduce_op2
    :   T_COUNT
    ;


inf
    :   T_P_INF
    |   T_M_INF
    ;


lhs
    :   scala
    |   field
    ;


lhs_list
    :   lhs
        ( ',' lhs_list )*
    ;


scala
    :   id
    ;


field
    :   id '.' id
    /*| id T_RARROW id                  { $$ = GM_field($1, $3, true);  }*/
    |   T_EDGE
        '(' id ')'
        '.' id
    ;


built_in
    :   id
        ( '.' id )?
        arg_list
    |   field
        '.' id
        arg_list
    ;


arg_list
    :   '(' expr_list? ')'
    ;


expr_list
    :   expr
    	( ',' expr_list )*
    ;


lhs_list2
    :   '<' lhs ';' lhs_list '>'
    ;


rhs_list2
    :   '<' expr ';' expr_list '>'
    ;


expr_user
    :   '[' 'XXX' ']'
    ;
/* USER_TEXT*/

id
    :   ID
    ;
