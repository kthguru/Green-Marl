tree grammar GMTreeParser;

options {
    tokenVocab   = GM;
    ASTLabelType = Tree;
}

@header {
    package parse;
    import ast.*;
    import inc.*;
}

prog
    :   proc_def*
    ;

proc_def
    :   proc_head
    	proc_body
        { FE.GM_procdef_finish(); }
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
    :   T_PROC x=id
        { FE.GM_procdef_begin(x, false); }
    |   T_LOCAL y=id
        { FE.GM_procdef_begin(y, true); }
    ;

arg_declist
    :   x=arg_decl
        { FE.GM_procdef_add_argdecl(x); }
        ( ',' x=arg_decl { FE.GM_procdef_add_argdecl(x); } )*
    ;

proc_return
    /* return of function should be always primitive type */
    :   prim_type
    |   node_type
    /*| graph_type */
    ;

arg_decl returns [ast_node value]
    :   x=arg_target y=typedecl
        { value = FE.GM_procdef_arg(x, y); }
    ;

arg_target returns [ast_node value]
    :   id_comma_list
        { value = FE.GM_finish_id_comma_list(); }
    ;

typedecl returns [ast_node value]
    :   u=graph_type
        { value = u; } 
    |   v=prim_type
    	{ value = v; }
/*  |   w=property
    |   x=nodeedge_type
    |   y=set_type*/
    ;

graph_type returns [ast_node value]
    :   T_GRAPH
        { value = FE.GM_graphtype_ref(GMTYPE_T.GMTYPE_GRAPH);  
          FE.GM_set_lineinfo(value, 0, 0); }
    ;


prim_type returns [ast_node value]
    :   u=T_INT		{ value = FE.GM_primtype_ref(GMTYPE_T.GMTYPE_INT);
                      FE.GM_set_lineinfo(value, 0, 0); }
    |   v=T_LONG	{ value = FE.GM_primtype_ref(GMTYPE_T.GMTYPE_LONG);
                      FE.GM_set_lineinfo(value, 0, 0); }
    |   w=T_FLOAT	{ value = FE.GM_primtype_ref(GMTYPE_T.GMTYPE_FLOAT);
                      FE.GM_set_lineinfo(value, 0, 0); }
    |   x=T_DOUBLE	{ value = FE.GM_primtype_ref(GMTYPE_T.GMTYPE_DOUBLE); 
                      FE.GM_set_lineinfo(value, 0, 0); }
    |   y=T_BOOL	{ value = FE.GM_primtype_ref(GMTYPE_T.GMTYPE_BOOL);
                      FE.GM_set_lineinfo(value, 0, 0); }
    ;

nodeedge_type returns [ast_node value]
    :   u=node_type
    	{ value = u; }
    |   v=edge_type
    	{ value = v; }
    ;


node_type returns [ast_node value]
    :   T_NODE x=id
    	{ value = FE.GM_nodetype_ref(x);
    	  FE.GM_set_lineinfo(value, 0, 0); }
    |   T_NODE
    	{ value = FE.GM_nodetype_ref(null);
    	  FE.GM_set_lineinfo(value, 0, 0);} 
    ;


edge_type returns [ast_node value]
    :   T_EDGE x=id
    	{ value = FE.GM_edgetype_ref(x);
    	  FE.GM_set_lineinfo(value, 0, 0);}
    |   T_EDGE
    	{ value = FE.GM_edgetype_ref(null);
    	  FE.GM_set_lineinfo(value, 0, 0);}
    ;

id_comma_list
    :   ( x=id { FE.GM_add_id_comma_list(x); } )*
    ;

proc_body returns [ast_node value]
    :   x=sent_block { FE.GM_procdef_setbody(x); }
    ;


sent_block returns [ast_node value]
    :   { FE.GM_start_sentblock(); }
        /*sent_list*/
        { value = FE.GM_finish_sentblock(); }
    ;

id returns [ast_node value]
    :   ID { value = FE.GM_id($ID.getText(), $ID.getLine(), $ID.getCharPositionInLine()); }
    ;