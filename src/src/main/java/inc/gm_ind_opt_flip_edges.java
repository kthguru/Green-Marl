package inc;

import ast.ast_procdef;
import opt.GlobalMembersGm_flip_edges;
import opt.gm_flip_find_candidate;

import common.GlobalMembersGm_argopts;
import common.GlobalMembersGm_main;
import common.GlobalMembersGm_traverse;

public class gm_ind_opt_flip_edges extends gm_compile_step
{
	private gm_ind_opt_flip_edges()
	{
		set_description("Flipping Edges in Nested Foeach");
	}
	public void process(ast_procdef p)
	{
		// find candidates
		gm_flip_find_candidate T = new gm_flip_find_candidate();
    
		// cannot set both options
		if (GlobalMembersGm_main.OPTIONS.is_arg_bool(GlobalMembersGm_argopts.GMARGFLAG_FLIP_PULL))
			T.set_to_avoid_pull_computation(true);
		else if (GlobalMembersGm_main.OPTIONS.is_arg_bool(GlobalMembersGm_argopts.GMARGFLAG_FLIP_REVERSE))
			T.set_to_avoid_reverse_edges(true);
		else
			return; // no need to do
    
		GlobalMembersGm_traverse.gm_traverse_sents(p, T);
    
		// apply flip
		GlobalMembersGm_flip_edges.do_flip_edges(T.get_target());
    
		return;
    
	}
	@Override
	public gm_compile_step get_instance()
	{
		return new gm_ind_opt_flip_edges();
	}
	public static gm_compile_step get_factory()
	{
		return new gm_ind_opt_flip_edges();
	}
}